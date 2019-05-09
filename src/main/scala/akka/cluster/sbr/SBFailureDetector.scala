package akka.cluster.sbr

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster._
import akka.cluster.sbr.SBFailureDetectorState.{Observer, Subject, Version}
import akka.cluster.sbr.SBReporter.IndirectlyConnectedMember
import akka.cluster.sbr.Util.pathAtAddress
import akka.cluster.sbr.implicits._
import cats.data.{OptionT, StateT}
import cats.effect.SyncIO
import cats.implicits._

import scala.concurrent.duration._

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
class SBFailureDetector(val sbReporter: ActorRef) extends Actor with ActorLogging with Stash with Timers {
  import SBFailureDetector._

  private val cluster           = Cluster(context.system)
  private val selfUniqueAddress = cluster.selfUniqueAddress
  private val failureDetector   = cluster.failureDetector

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case _: CurrentClusterState =>
      unstashAll()
      context.become(active(SBFailureDetectorState(self.path)))

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
      context.become(active(contention(node, observer, subject, version, sender()).runS(state).unsafeRunSync()))

    case UnreachabilityContentionAck(key, version) =>
      context.become(active(contentionAck(key, version).runS(state).unsafeRunSync()))

    case SendContention(to, contention) =>
      sendConnectionWithRetry(to, contention).unsafeRunSync()
  }

  /**
   * Broadcast contentions if the current node sees unreachable nodes as reachable.
   * Otherwise, send the updated reachability to the reporter.
   *
   * Sends the contentions with at-least-once delivery semantics.
   */
  private def reachabilityChanged(reachability: Reachability): StateT[SyncIO, SBFailureDetectorState, Unit] = {
    lazy val sbFailureDetectors: SyncIO[List[ActorPath]] = SyncIO(cluster.state.members.toList.map { member =>
      pathAtAddress(member.address, self.path)
    })

    def cancelPrevious(path: ActorPath, contention: UnreachabilityContention): SyncIO[Unit] =
      SyncIO(timers.cancel(keyFor(path, contention)))

    def sendContention(path: ActorPath,
                       contention: UnreachabilityContention): StateT[SyncIO, SBFailureDetectorState, Unit] =
      for {
        _ <- StateT.liftF(sendConnectionWithRetry(path, contention))
        _ <- StateT.liftF(SyncIO(log.debug("ADDING {}", keyFor(path, contention))))
        _ <- StateT.modify[SyncIO, SBFailureDetectorState](_.expectAck(keyFor(path, contention), contention.version))
      } yield ()

    def broadcastContention(record: Reachability.Record): StateT[SyncIO, SBFailureDetectorState, Unit] =
      for {
        sbFailureDetectors <- StateT.liftF[SyncIO, SBFailureDetectorState, List[ActorPath]](sbFailureDetectors)
        state <- StateT.modifyF[SyncIO, SBFailureDetectorState](sbFailureDetectors.foldLeftM(_) {
          case (state, path) =>
            val contention =
              UnreachabilityContention(selfUniqueAddress, record.observer, record.subject, record.version)

            for {
              // Cancel the timer for the previous observer, subject pair contention
              // as there is only one timer per such pair. There's no need to make sure
              // it was delivered, the new contention will override it.
              _ <- cancelPrevious(path, contention)

              state <- sendContention(path, contention).runS(state) // todo without runS?

              // Schedules a to send a message to itself
              _ <- resendAfter(1.second, path, contention)
            } yield state
        })
      } yield state

    def broadcastContentions(observer: Observer, subject: Subject): StateT[SyncIO, SBFailureDetectorState, Unit] =
      reachability
        .recordsFrom(observer)
        .find(r => r.subject === subject && r.status === Reachability.Unreachable) // find the record describing that `observer` sees `subject` as unreachable
        .map(broadcastContention)
        .getOrElse(StateT.liftF(SyncIO.unit))

    def isLocallyReachable(node: UniqueAddress): StateT[SyncIO, SBFailureDetectorState, Boolean] =
      StateT.liftF[SyncIO, SBFailureDetectorState, Boolean](
        SyncIO(failureDetector.isMonitoring(node.address) && failureDetector.isAvailable(node.address))
      )

    reachability.observersGroupedByUnreachable.toList
      .traverse_ {
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
    if (node === selfUniqueAddress) {
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
      OptionT(SyncIO(cluster.state.members.find(_.uniqueAddress === node)))

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
   * Register the contention initiated by `node` of the observation by `observer` of `subject` as unreachable.
   */
  private def contention(node: UniqueAddress,
                         observer: UniqueAddress,
                         subject: UniqueAddress,
                         version: Long,
                         sender: ActorRef): StateT[SyncIO, SBFailureDetectorState, Unit] = {
    val ack = SyncIO(
      sender ! UnreachabilityContentionAck(
        ContentionKey(pathAtAddress(selfUniqueAddress.address, self.path), observer, subject),
        version
      )
    )

    for {
      _ <- StateT.modify[SyncIO, SBFailureDetectorState](_.contention(node, observer, subject, version))
      _ <- sendReachability(subject)
      _ <- StateT.liftF(ack)
    } yield ()
  }

  /**
   * Cancel the timer related to the ack.
   *
   * Nothing is done if the version is different from the expected one.
   */
  private def contentionAck(key: ContentionKey, version: Version): StateT[SyncIO, SBFailureDetectorState, Unit] =
    StateT.modifyF { state =>
      state.waitingForAck.get(key).fold(SyncIO.pure(state)) { v =>
        if (v == version) {
          SyncIO(timers.cancel(key)).map(_ => state.receivedAck(key))
        } else {
          // Received ack for old contention or one from the future :/
          SyncIO.pure(state)
        }
      }
    }

  private def keyFor(path: ActorPath, contention: UnreachabilityContention): ContentionKey =
    ContentionKey(path, contention.observer, contention.subject)

  /**
   * Schedules an event to resend the contention to `to`.
   */
  private def resendAfter(timeout: FiniteDuration, to: ActorPath, contention: UnreachabilityContention): SyncIO[Unit] =
    SyncIO(timers.startSingleTimer(keyFor(to, contention), SendContention(to, contention), timeout))

  /**
   * Send the contention to `to` expecting an ack. If an ack is not received in 1 second the actor
   * will retry.
   */
  private def sendConnectionWithRetry(to: ActorPath, contention: UnreachabilityContention): SyncIO[Unit] =
    SyncIO(context.system.actorSelection(to) ! contention) >> resendAfter(1.second, to, contention)

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
    context.system.eventStream.subscribe(self, classOf[ReachabilityChanged])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    context.system.eventStream.unsubscribe(self)
    timers.cancelAll()
  }
}

object SBFailureDetector {
  def props(sendTo: ActorRef): Props = Props(new SBFailureDetector(sendTo))

  sealed abstract class SBRReachability extends Product with Serializable
  final case object Reachable           extends SBRReachability
  final case object Unreachable         extends SBRReachability
  final case object IndirectlyConnected extends SBRReachability

  final case class ContentionKey(path: ActorPath, observer: Observer, subject: Subject)

  final case class UnreachabilityContention(node: UniqueAddress, observer: Observer, subject: Subject, version: Version)
  final case class UnreachabilityContentionAck(key: ContentionKey, version: Version)

  final case class SendContention(to: ActorPath, contention: UnreachabilityContention)
}
