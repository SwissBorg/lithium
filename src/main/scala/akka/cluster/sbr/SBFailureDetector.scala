package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.cluster.ClusterEvent._
import akka.cluster._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, Unsubscribe}
import akka.cluster.sbr.SBFailureDetectorState.{Observer, Subject}
import akka.cluster.sbr.SBReporter.IndirectlyConnectedMember
import cats.data.{OptionT, StateT}
import cats.effect.SyncIO
import cats.implicits._

/**
 * Actor reporting the reachability status of cluster members based on
 * `akka.cluster.Reachability`. Essentially, it adds the [[IndirectlyConnectedNode]]
 * status to the reachability events.
 *
 * A node is indirectly connected when only some of its observers see it as unreachable.
 * This might happen for instance when the link between two nodes is faulty,
 * they cannot directly communicate but can via another node.
 *
 * @param sbReporter the actor to which the reachability events have to be sent.
 */
class SBFailureDetector(val sbReporter: ActorRef) extends Actor with ActorLogging with Stash {
  import SBFailureDetector._

  private val cluster           = Cluster(context.system)
  private val selfUniqueAddress = cluster.selfUniqueAddress
  private val failureDetector   = cluster.failureDetector

  private val mediator: ActorRef = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("sbr", self)

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case _: CurrentClusterState =>
      unstashAll()
      context.become(active(SBFailureDetectorState.empty))

    case _ => stash()
  }

  private def active(state: SBFailureDetectorState): Receive = {
    case ReachabilityChanged(r) =>
      context.become(active(reachabilityChanged(r).runS(state).unsafeRunSync()))

    case MemberRemoved(m, _) =>
      context.become(active(remove(m.uniqueAddress).runS(state).unsafeRunSync()))

    case ReachableMember(m) =>
      context.become(active(reachable(m.uniqueAddress).runS(state).unsafeRunSync()))

    case UnreachabilityContention(node, observer, subject, version) =>
      context.become(active(contention(node, observer, subject, version).runS(state).unsafeRunSync()))
  }

  /**
   * Broadcast contentions if the current node sees unreachable nodes as reachable.
   * Otherwise, send the updated reachability to the reporter.
   */
  private def reachabilityChanged(reachability: Reachability): StateT[SyncIO, SBFailureDetectorState, Unit] = {
    def broadcastContentions(observer: Observer, subject: Subject): StateT[SyncIO, SBFailureDetectorState, Unit] =
      StateT.liftF(
        reachability
          .recordsFrom(observer)
          .find(r => r.subject == subject && r.status == Reachability.Unreachable) // find the record describing that `observer` sees `subject` as unreachabl
          .traverse_(record => broadcastContention(record.observer, record.subject, record.version))
      )

    def isLocallyReachable(node: UniqueAddress): StateT[SyncIO, SBFailureDetectorState, Boolean] =
      StateT.liftF[SyncIO, SBFailureDetectorState, Boolean](
        SyncIO(failureDetector.isMonitoring(node.address) && failureDetector.isAvailable(node.address))
      )

    reachability.observersGroupedByUnreachable.toList.traverse_ {
      case (subject, observers) =>
        observers.toList.traverse_[StateT[SyncIO, SBFailureDetectorState, ?], Unit] { observer =>
          for {
            _ <- unreachable(observer, subject)
            _ <- isLocallyReachable(subject).ifM(broadcastContentions(observer, subject), sendReachability(subject))
          } yield ()
        }
    }
  }

  /**
   * Register the node as removed.
   *
   * If the removed node is the current one the actor will stop itself.
   */
  private def remove(node: UniqueAddress): StateT[SyncIO, SBFailureDetectorState, Unit] =
    if (node == selfUniqueAddress) {
      // This node is being stopped. Kill the actor
      // to stop any further updates.
      StateT.liftF(SyncIO(context.stop(self)))
    } else {
      StateT.modify(_.remove(node))
    }

  /**
   * Register the node as reachable and inform the reporter of it.
   */
  private def reachable(node: UniqueAddress): StateT[SyncIO, SBFailureDetectorState, Unit] =
    StateT.modify[SyncIO, SBFailureDetectorState](_.reachable(node)) >> sendReachability(node)

  /**
   * Set the subject as unreachable from the observer.
   */
  private def unreachable(observer: Observer, subject: Subject): StateT[SyncIO, SBFailureDetectorState, Unit] =
    StateT.modify(_.unreachable(observer, subject))

  /**
   * Idempotently send the reachability of `node` to the reporter.
   */
  private def sendReachability(node: UniqueAddress): StateT[SyncIO, SBFailureDetectorState, Unit] = {
    def memberFromAddress(node: UniqueAddress): OptionT[SyncIO, Member] =
      OptionT(SyncIO(cluster.state.members.find(_.uniqueAddress == node)))

    StateT.modifyF { state =>
      val (status, state0) = state.updatedStatus(node)

      val sendStatus = status
        .traverse_ { s =>
          memberFromAddress(node).semiflatMap { m =>
            SyncIO(sbReporter ! (s match {
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
  }

  /**
   * Broadcast the contention to all the [[SBFailureDetector]]s in the cluster.
   *
   * Note: the version has to increase between every call for each `observer`, `subject` pair.
   */
  private def broadcastContention(observer: UniqueAddress, subject: UniqueAddress, version: Long): SyncIO[Unit] =
    SyncIO(mediator ! Publish("sbr", UnreachabilityContention(selfUniqueAddress, observer, subject, version)))

  /**
   * Register the contention initiated by `node` of the observation by `observer` of `subject` as unreachable.
   */
  private def contention(node: UniqueAddress,
                         observer: UniqueAddress,
                         subject: UniqueAddress,
                         version: Long): StateT[SyncIO, SBFailureDetectorState, Unit] =
    StateT.modify[SyncIO, SBFailureDetectorState](_.contention(node, observer, subject, version)) >>
      sendReachability(subject)

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
    context.system.eventStream.subscribe(self, classOf[ReachabilityChanged])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    context.system.eventStream.unsubscribe(self)
    mediator ! Unsubscribe("sbr", self)
  }
}

object SBFailureDetector {
  def props(sendTo: ActorRef): Props = Props(new SBFailureDetector(sendTo))

  sealed abstract class SBRReachability extends Product with Serializable
  final case object Reachable           extends SBRReachability
  final case object Unreachable         extends SBRReachability
  final case object IndirectlyConnected extends SBRReachability

  final case class UnreachabilityContention(node: UniqueAddress, observer: Observer, subject: Subject, version: Long)
}
