package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.cluster.ClusterEvent._
import akka.cluster._

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
 *
 * @param sendTo the actor to which the event have to be sent.
 */
class SBRFailureDetector(val sendTo: ActorRef) extends Actor with ActorLogging with Stash {
  import SBRFailureDetector._

  private val cluster                          = Cluster(context.system)
  private val selfUniqueAddress: UniqueAddress = cluster.selfUniqueAddress

  private var _state: SBRFailureDetectorState =
    SBRFailureDetectorState(cluster.selfMember, cluster.settings.MonitoredByNrOfMembers)

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
    s.members.foreach { member =>
      _state = _state.add(member)
    }

    s.unreachable.foreach { member =>
      _state = _state.unreachable(member)
    }
  }

  /**
   * Sends the updated reachability states to the parent.
   */
  private def reachabilityChanged(r: Reachability): Unit = {

    /**
     * Sends the `reachability` to the parent if it
     * previously was in a different reachability state.
     */
    def send(reachability: SBRReachability, member: Member): Unit =
      _state.lastReachabilities.get(member.uniqueAddress) match {
        case Some(`reachability`) => ()
        case _ =>
          sendTo ! (reachability match {
            case Reachable           => ReachableMember(member)
            case Unreachable         => UnreachableMember(member)
            case IndirectlyConnected => IndirectlyConnectedNode(member)
          })
          _state = _state.update(member, reachability)
      }

    for {
      (node, unreachableFrom) <- r.observersGroupedByUnreachable
      member                  <- memberFromAddress(node)
    } {
      val allReachable = cluster.state.members.map(_.uniqueAddress) -- r.allUnreachableFrom(selfUniqueAddress)

      if (unreachableFrom.isEmpty) {
        send(Reachable, member)
      } else {
        // True if there is at least a reachable
        // observer node that can reach the current member.
        val isIndirectlyReachable =
          _state.ring
            .receivers(node)
            .exists(m => allReachable.contains(m) && !unreachableFrom.contains(m))

        if (isIndirectlyReachable) {
          send(IndirectlyConnected, member)
        } else {
          send(Unreachable, member)
        }
      }
    }
  }

  private def memberFromAddress(node: UniqueAddress): Option[Member] =
    cluster.state.members.find(_.uniqueAddress == node)

  private def addMember(m: Member): Unit = _state = _state.add(m)

  private def removeMember(m: Member): Unit =
    if (m.uniqueAddress == selfUniqueAddress) {
      // This node is being stopped. Kill the actor
      // to stop any further updates.
      context.stop(self)
    } else {
      _state = _state.remove(m)
    }

  private def reachableMember(m: Member): Unit = {
    _state = _state.reachable(m)

    // No need to check as all the monitoring nodes see the member as reachable.
    sendTo ! ReachableMember(m)
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
  def props(sendTo: ActorRef): Props = Props(new SBRFailureDetector(sendTo))

  sealed abstract class SBRReachability extends Product with Serializable
  final case object Reachable           extends SBRReachability
  final case object Unreachable         extends SBRReachability
  final case object IndirectlyConnected extends SBRReachability
}
