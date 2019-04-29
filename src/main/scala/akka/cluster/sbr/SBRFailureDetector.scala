package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.cluster.ClusterEvent._
import akka.cluster._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, Unsubscribe}
import akka.cluster.sbr.SBRFailureDetectorState.{Observer, Subject}

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

  private val cluster           = Cluster(context.system)
  private val selfUniqueAddress = cluster.selfUniqueAddress
  private val failureDetector   = cluster.failureDetector

  private var _state: SBRFailureDetectorState = SBRFailureDetectorState.empty

  private val mediator: ActorRef = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("sbr", self)

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case s: CurrentClusterState =>
//      init(s)
      unstashAll()
      context.become(active)

    case _ => stash()
  }

  private def active: Receive = {
    case ReachabilityChanged(r)                                     => reachabilityChanged(r)
    case MemberRemoved(m, _)                                        => removeMember(m.uniqueAddress)
    case ReachableMember(m)                                         => reachable(m.uniqueAddress)
    case UnreachabilityContention(node, observer, subject, version) => contention(node, observer, subject, version)
  }

  private def isLocallyReachable(unreachableNode: UniqueAddress): Boolean =
    failureDetector.isMonitoring(unreachableNode.address) && failureDetector.isAvailable(unreachableNode.address)

  private def reachabilityChanged(r: Reachability): Unit =
    r.observersGroupedByUnreachable.foreach {
      case (subject, observers) =>
        observers.foreach { observer =>
          unreachable(observer, subject)

          if (isLocallyReachable(subject)) {
            r.recordsFrom(observer)
              .find(r => r.subject == subject && r.status == Reachability.Unreachable)
              .foreach { record =>
                publishContention(record.observer, record.subject, record.version)
              }
          } else {
            sendStatus(subject)
          }
        }
    }

  private def memberFromAddress(node: UniqueAddress): Option[Member] =
    cluster.state.members.find(_.uniqueAddress == node)

  private def removeMember(node: UniqueAddress): Unit =
    if (node == selfUniqueAddress) {
      // This node is being stopped. Kill the actor
      // to stop any further updates.
      context.stop(self)
    } else {
      _state = _state.remove(node)
    }

  private def reachable(node: UniqueAddress): Unit = {
    _state = _state.reachable(node)
    sendStatus(node)
  }

  private def unreachable(observer: Observer, subject: Subject): Unit = _state = _state.unreachable(observer, subject)

  private def sendStatus(node: UniqueAddress): Unit = {
    val (status, state) = _state.status(node)
    _state = state
    status.foreach(
      s =>
        memberFromAddress(node).foreach { m =>
          sendTo ! (s match {
            case Reachable           => ReachableMember(m)
            case IndirectlyConnected => IndirectlyConnectedNode(m)
            case Unreachable         => UnreachableMember(m)
          })
      }
    )
  }

  private def publishContention(observer: UniqueAddress, subject: UniqueAddress, version: Long): Unit =
    mediator ! Publish("sbr", UnreachabilityContention(selfUniqueAddress, observer, subject, version))

  private def contention(node: UniqueAddress, observer: UniqueAddress, subject: UniqueAddress, version: Long): Unit = {
    _state = _state.contention(node, observer, subject, version)
    sendStatus(subject)
  }

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
    context.system.eventStream.subscribe(self, classOf[ReachabilityChanged])
    context.system.eventStream.subscribe(self, classOf[MemberRemoved])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    context.system.eventStream.unsubscribe(self)
    mediator ! Unsubscribe("sbr", self)
  }
}

object SBRFailureDetector {
  def props(sendTo: ActorRef): Props = Props(new SBRFailureDetector(sendTo))

  sealed abstract class SBRReachability extends Product with Serializable
  final case object Reachable           extends SBRReachability
  final case object Unreachable         extends SBRReachability
  final case object IndirectlyConnected extends SBRReachability

  final case class IndirectlyConnected(member: Member, versions: Map[UniqueAddress, Long])
  final case class UnreachabilityContention(node: UniqueAddress,
                                            observer: UniqueAddress,
                                            subject: UniqueAddress,
                                            version: Long)
}
