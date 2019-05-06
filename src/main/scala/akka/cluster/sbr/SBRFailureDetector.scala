package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.cluster.ClusterEvent._
import akka.cluster._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, Unsubscribe}
import akka.cluster.sbr.SBRFailureDetectorState.{Observer, Subject}
import akka.cluster.sbr.StabilityReporter.IndirectlyConnectedMember
import cats.data.{OptionT, StateT}
import cats.effect.SyncIO
import cats.implicits._

/**
 * Actor reporting the reachability status of cluster members based on
 * `akka.cluster.Reachability`. Essentially, it adds the [[IndirectlyConnectedNode]]
 * status to the reachability events.
 *
 * A node is indirectly connected when not all nodes can communicate
 * with it. This might happen for instance when the link between
 * two nodes is faulty, they cannot directly communicate but can
 * via another node.
 *
 * @param sendTo the actor to which the reachability events have to be sent.
 */
class SBRFailureDetector(val sendTo: ActorRef) extends Actor with ActorLogging with Stash {
  import SBRFailureDetector._

  private val cluster           = Cluster(context.system)
  private val selfUniqueAddress = cluster.selfUniqueAddress
  private val failureDetector   = cluster.failureDetector

  private val mediator: ActorRef = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("sbr", self)

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case s: CurrentClusterState =>
//      init(s)
      unstashAll()
      context.become(active(SBRFailureDetectorState.empty))

    case _ => stash()
  }

  private def active(state: SBRFailureDetectorState): Receive = {
    case ReachabilityChanged(r) =>
      context.become(active(reachabilityChanged(r).runS(state).unsafeRunSync()))

    case MemberRemoved(m, _) =>
      context.become(active(removeMember(m.uniqueAddress).runS(state).unsafeRunSync()))

    case ReachableMember(m) =>
      context.become(active(reachable(m.uniqueAddress).runS(state).unsafeRunSync()))

    case UnreachabilityContention(node, observer, subject, version) =>
      context.become(active(contention(node, observer, subject, version).runS(state).unsafeRunSync()))
  }

  private def isLocallyReachable(unreachableNode: UniqueAddress): SyncIO[Boolean] =
    SyncIO(
      failureDetector.isMonitoring(unreachableNode.address) && failureDetector.isAvailable(unreachableNode.address)
    )

  private def reachabilityChanged(r: Reachability): StateT[SyncIO, SBRFailureDetectorState, Unit] = {
    def publishContentions(observer: Observer, subject: Subject): StateT[SyncIO, SBRFailureDetectorState, Unit] =
      StateT.liftF(
        r.recordsFrom(observer)
          .find(r => r.subject == subject && r.status == Reachability.Unreachable)
          .traverse_(record => publishContention(record.observer, record.subject, record.version))
      )

    r.observersGroupedByUnreachable.toList.traverse_ {
      case (subject, observers) =>
        observers.toList.traverse_[StateT[SyncIO, SBRFailureDetectorState, ?], Unit] { observer =>
          for {
            _ <- unreachable(observer, subject)
            _ <- StateT
              .liftF[SyncIO, SBRFailureDetectorState, Boolean](isLocallyReachable(subject))
              .ifM(publishContentions(observer, subject), sendStatus(subject))
          } yield ()
        }
    }
  }

  private def memberFromAddress(node: UniqueAddress): OptionT[SyncIO, Member] =
    OptionT(SyncIO(cluster.state.members.find(_.uniqueAddress == node)))

  private def removeMember(node: UniqueAddress): StateT[SyncIO, SBRFailureDetectorState, Unit] =
    if (node == selfUniqueAddress) {
      // This node is being stopped. Kill the actor
      // to stop any further updates.
      StateT.liftF(SyncIO(context.stop(self)))
    } else {
      StateT.modify(_.remove(node))
    }

  private def reachable(node: UniqueAddress): StateT[SyncIO, SBRFailureDetectorState, Unit] =
    StateT.modify[SyncIO, SBRFailureDetectorState](_.reachable(node)) >> sendStatus(node)

  private def unreachable(observer: Observer, subject: Subject): StateT[SyncIO, SBRFailureDetectorState, Unit] =
    StateT.modify(_.unreachable(observer, subject))

  private def sendStatus(node: UniqueAddress): StateT[SyncIO, SBRFailureDetectorState, Unit] = StateT.modifyF { state =>
    val (status, state0) = state.updatedStatus(node)

    val sendStatus = status
      .traverse_ { s =>
        memberFromAddress(node).semiflatMap { m =>
          SyncIO(sendTo ! (s match {
            case Reachable           => ReachableMember(m)
            case IndirectlyConnected => IndirectlyConnectedMember(m)
            case Unreachable         => UnreachableMember(m)
          }))
        }
      }
      .value
      .void

    sendStatus.map(_ => state0)
  }

  private def publishContention(observer: UniqueAddress, subject: UniqueAddress, version: Long): SyncIO[Unit] =
    SyncIO(mediator ! Publish("sbr", UnreachabilityContention(selfUniqueAddress, observer, subject, version)))

  private def contention(node: UniqueAddress,
                         observer: UniqueAddress,
                         subject: UniqueAddress,
                         version: Long): StateT[SyncIO, SBRFailureDetectorState, Unit] =
    StateT.modify[SyncIO, SBRFailureDetectorState](_.contention(node, observer, subject, version)) >> sendStatus(
      subject
    )

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

  final case class UnreachabilityContention(node: UniqueAddress,
                                            observer: UniqueAddress,
                                            subject: UniqueAddress,
                                            version: Long)
}
