package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.cluster.ClusterEvent._
import akka.cluster.ClusterSettings.DataCenter
import akka.cluster.sbr.SBRFailureDetector.SBRReachability
import akka.cluster.{Cluster, HeartbeatNodeRing, Member, Reachability, UniqueAddress}

/**
 * Actor reporting the reachability status of cluster members based on
 * `akka.cluster.Reachability`. Essentially, it adds the [[IndirectlyConnectedNode]]
 * status to the reachability events.
 *
 * A node is indirectly connected when all the observers do not have
 * the same opinion. This can happen when a link between two nodes is
 * faulty and cannot communicate anymore. In this case the two nodes
 * see each other as unreachable (which will be gossiped) but could
 * still reach other by taking a different path.
 *
 * This failure detector is eventually consistent.
 */
class SBRFailureDetector extends Actor with ActorLogging with Stash {
  import SBRFailureDetector._

  private val cluster                          = Cluster(context.system)
  private val parent                           = context.parent
  private val selfUniqueAddress: UniqueAddress = cluster.selfUniqueAddress
  private val selfDC: DataCenter               = cluster.selfDataCenter

  private var _state: SBRFailureDetectorState = SBRFailureDetectorState(
    HeartbeatNodeRing(selfUniqueAddress, Set(selfUniqueAddress), Set.empty, cluster.settings.MonitoredByNrOfMembers),
    Map.empty,
    cluster.selfMember
  )

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case s: CurrentClusterState =>
      init(s)
      unstashAll()
      context.become(active)

    case _ => stash()
  }

  private def active: Receive = {
    case ReachabilityChanged(r) => reachabilityChanged(r)
    case MemberRemoved(m, _)    => removeMember(m)
    case e: MemberEvent         => addMember(e.member)
    case ReachableMember(m)     => reachableMember(m)
    case UnreachableMember(m)   => unreachableMember(m)
  }

  private def init(s: CurrentClusterState): Unit = {
    val nodes       = s.members.collect { case m if m.dataCenter == selfDC     => m.uniqueAddress }
    val unreachable = s.unreachable.collect { case m if m.dataCenter == selfDC => m.uniqueAddress }

    _state = _state.copy(ring = _state.ring.copy(nodes = nodes + selfUniqueAddress, unreachable = unreachable))
  }

  private def reachabilityChanged(r: Reachability): Unit = {
    def idempotentSend(reachability: SBRReachability, member: Member): Unit =
      _state.lastReachabilities.get(member.uniqueAddress) match {
        case Some(`reachability`) => ()
        case _ =>
          parent ! (reachability match {
            case Reachable           => ReachableMember(member)
            case Unreachable         => UnreachableMember(member)
            case IndirectlyConnected => IndirectlyConnectedNode(member)
          })
          _state = _state.update(member, reachability)
      }

    r.observersGroupedByUnreachable.foreach {
      case (node, unreachableFrom) =>
        val member       = cluster.state.members.find(_.uniqueAddress == node).get // todo better handle potential error
        val allReachable = cluster.state.members.map(_.uniqueAddress) -- r.allUnreachableFrom(selfUniqueAddress)

        if (unreachableFrom.isEmpty) {
          idempotentSend(Reachable, member)
        } else {
          // True if there is at least a reachable
          // observer node that can reach the current member.
          val isIndirectlyReachable =
            _state.ring
              .receivers(node)
              .exists(m => allReachable.contains(m) && !unreachableFrom.contains(m))

          if (isIndirectlyReachable) {
            idempotentSend(IndirectlyConnected, member)
          } else {
            idempotentSend(Unreachable, member)
          }
        }
    }
  }

  private def addMember(m: Member): Unit = _state = _state.add(m)

  private def removeMember(m: Member): Unit = {
    if (m.uniqueAddress == selfUniqueAddress) {
      // This node is being stopped. Kill the actor
      // to stop any further updates.
      context.stop(self)
    }

    _state = _state.remove(m)
  }

  private def reachableMember(m: Member): Unit = {
    _state = _state.reachable(m)

    // No need to check as all the monitoring nodes see the member as reachable.
    parent ! ReachableMember(m)
  }

  private def unreachableMember(m: Member): Unit = _state = _state.unreachable(m)

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
    context.system.eventStream.subscribe(self, classOf[ReachabilityChanged])
    context.system.eventStream.subscribe(self, classOf[MemberRemoved])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    context.system.eventStream.unsubscribe(self)
  }
}

object SBRFailureDetector {
  def props: Props = Props(new SBRFailureDetector)

  sealed abstract class SBRReachability extends Product with Serializable
  final case object Reachable           extends SBRReachability
  final case object Unreachable         extends SBRReachability
  final case object IndirectlyConnected extends SBRReachability
}

/**
 * State of the SBRFailureDetector.
 *
 * Keeps the `ring` in sync with the one in `ClusterHeartBeatSender`
 * to known who the current observers are of specific nodes.
 */
final private[sbr] case class SBRFailureDetectorState(ring: HeartbeatNodeRing,
                                                      lastReachabilities: Map[UniqueAddress, SBRReachability],
                                                      private val selfMember: Member) {
  def add(m: Member): SBRFailureDetectorState = {
    val node = m.uniqueAddress
    if (node != selfMember.uniqueAddress && !ring.nodes(node) && selfMember.dataCenter == m.dataCenter) {
      copy(ring = ring :+ node)
    } else {
      this
    }
  }

  def remove(m: Member): SBRFailureDetectorState = {
    val lastReachabilities0 = lastReachabilities - m.uniqueAddress

    if (m.dataCenter == selfMember.dataCenter) {
      copy(ring = ring :- m.uniqueAddress, lastReachabilities = lastReachabilities0)
    } else {
      copy(lastReachabilities = lastReachabilities0)
    }
  }

  def reachable(m: Member): SBRFailureDetectorState =
    copy(ring = ring.copy(unreachable = ring.unreachable + m.uniqueAddress))

  def unreachable(m: Member): SBRFailureDetectorState =
    copy(ring = ring.copy(unreachable = ring.unreachable - m.uniqueAddress))

  def update(member: Member, reachability: SBRReachability): SBRFailureDetectorState =
    copy(lastReachabilities = lastReachabilities + (member.uniqueAddress -> reachability))
}
