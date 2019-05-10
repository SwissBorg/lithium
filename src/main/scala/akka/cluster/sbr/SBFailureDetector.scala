package akka.cluster.sbr

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster._
import akka.cluster.sbr.SBFailureDetectorState.{Observer, Subject, Version}
import akka.cluster.sbr.SBReporter.IndirectlyConnectedMember
import akka.cluster.sbr.Util.pathAtAddress
import akka.cluster.sbr.implicits._
import cats.data.{OptionT, StateT}
import cats.data.StateT._
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
      context.become(active(updateReachabilities(r).runS(state).unsafeRunSync()))

    case MemberRemoved(m, _) =>
      context.become(active(remove(m.uniqueAddress).runS(state).unsafeRunSync()))

    case ReachableMember(m) =>
      context.become(active(withReachable(m.uniqueAddress).runS(state).unsafeRunSync()))

    case c: Contention =>
      context.become(active(withContentionFrom(sender(), c).runS(state).unsafeRunSync()))

    case ack: ContentionAck =>
      context.become(active(contentionAck(ack).runS(state).unsafeRunSync()))

    case SendContention(to, contention) =>
      context.become(active(sendContentionWithRetry(contention, to).runS(state).unsafeRunSync()))
  }

  /**
   * Broadcast contentions if the current node sees unreachable nodes as reachable.
   * Otherwise, send the updated reachability to the reporter.
   *
   * Sends the contentions with at-least-once delivery semantics.
   */
  private def updateReachabilities(reachability: Reachability): Eval[Unit] = {

    /**
     * Attemps to find the record of `observer` that describes
     * `subject` as unreachable.
     *
     * Assumes that there's only one record per observer, subject pair in the
     * `Reachability` data structure.
     */
    def unreachableRecord(observer: Observer, subject: Subject): Option[Contention] =
      reachability
        .recordsFrom(observer)
        .find { r =>
          r.subject === subject && r.status === Reachability.Unreachable // find the record describing that `observer` sees `subject` as unreachable
        }
        .map(r => Contention(selfUniqueAddress, r.observer, r.subject, r.version))

    reachability.observersGroupedByUnreachable.toList
      .traverse_ {
        case (subject, observers) =>
          observers.toList.traverse_ { observer =>
            for {
              _ <- withUnreachable(observer, subject)
              _ <- isLocallyReachable(subject).flatMap {
                case LocallyReachable             => unreachableRecord(observer, subject).traverse_(broadcastContentionWithRetry)
                case LocallyUnreachable | Unknown => sendReachability(subject)
              }
            } yield ()
          }
      }
  }

  /**
   * Broadcast with at-least-once delivery the contention to all the `SBFailureDetector`s
   * that exist on the cluster members, including itself.
   */
  private def broadcastContentionWithRetry(contention: Contention): Eval[Unit] =
    for {
      sbFailureDetectors <- sbFailureDetectors
      state <- sbFailureDetectors.traverse_ { to =>
        val ack = ContentionAck.fromContention(contention, to)
        val key = ContentionKey.fromAck(ack)

        for {
          // Cancel the timer for the previous observer, subject pair contention
          // as there is only one timer per such pair. There's no need to make sure
          // it was delivered, the new contention will override it.
          _ <- cancelContentionResend(key)
          _ <- sendContentionWithRetry(contention, to)
          _ <- expectAck(ack)
        } yield ()
      }
    } yield state

  private def expectAck(ack: ContentionAck): Eval[Unit] = modify(_.expectAck(ack))

  private def isLocallyReachable(node: UniqueAddress): Eval[LocalReachability] =
    liftF(SyncIO {
      if (failureDetector.isMonitoring(node.address)) {
        if (failureDetector.isAvailable(node.address)) LocallyReachable
        else LocallyUnreachable
      } else Unknown
    })

  /**
   * All the instances of this actor living on the other cluster nodes.
   */
  private val sbFailureDetectors: Eval[List[ActorPath]] = liftF(SyncIO(cluster.state.members.toList.map { member =>
    pathAtAddress(member.address, self.path)
  }))

  /**
   * Register the node as removed.
   *
   * If the removed node is the current one the actor will stop itself.
   */
  private def remove(node: UniqueAddress): Eval[Unit] =
    if (node === selfUniqueAddress) {
      // This node is being stopped. Kill the actor
      // to stop any further updates.
      liftF(SyncIO(context.stop(self)))
    } else {
      modify(_.remove(node))
    }

  /**
   * Register the node as reachable and inform the reporter of it.
   */
  private def withReachable(node: UniqueAddress): Eval[Unit] =
    for {
      _ <- modify[SyncIO, SBFailureDetectorState](_.withReachable(node))
      _ <- sendReachability(node)
    } yield ()

  /**
   * Set the subject as unreachable from the observer.
   */
  private def withUnreachable(observer: Observer, subject: Subject): Eval[Unit] =
    modify(_.withUnreachableFrom(observer, subject))

  /**
   * Send the reachability of `node` to the reporter.
   *
   * If it is the same as the previous time this function was called
   * it will do nothing.
   */
  private def sendReachability(node: UniqueAddress): Eval[Unit] =
    modifyF { state =>
      val (status, state0) = state.updatedStatus(node)

      val sendStatus = status
        .traverse_ { reachability =>
          memberFromAddress(node).semiflatMap { m =>
            SyncIO(sbReporter ! (reachability match {
              case Reachable           => ReachableMember(m)
              case IndirectlyConnected => IndirectlyConnectedMember(m)
              case Unreachable         => UnreachableMember(m)
            }))
          }
        }
        .value
        .void

      sendStatus.as(state0)
    }

  private def memberFromAddress(node: UniqueAddress): OptionT[SyncIO, Member] =
    OptionT(SyncIO(cluster.state.members.find(_.uniqueAddress === node)))

  /**
   * Add the contention and acknowledge the sender that it was received.
   */
  private def withContentionFrom(sender: ActorRef, contention: Contention): Eval[Unit] =
    for {
      _ <- withContention(contention)
      _ <- sendReachability(contention.subject)
      _ <- ackContention(sender, contention)
    } yield ()

  private def withContention(contention: Contention): Eval[Unit] = modify(
    _.withContention(contention.protester, contention.observer, contention.subject, contention.version)
  )

  private def ackContention(sender: ActorRef, contention: Contention): Eval[Unit] = liftF(
    SyncIO(
      sender ! ContentionAck.fromContention(contention, pathAtAddress(selfUniqueAddress.address, self.path))
    )
  )

  /**
   * Cancel the timer related to the ack.
   */
  private def contentionAck(ack: ContentionAck): Eval[Unit] =
    for {
      _ <- cancelContentionResend(ContentionKey.fromAck(ack))
      _ <- receivedAck(ack)
    } yield ()

  private def cancelContentionResend(key: ContentionKey): Eval[Unit] = liftF(SyncIO(timers.cancel(key)))

  private def receivedAck(ack: ContentionAck): Eval[Unit] = modify(_.receivedAck(ack))

  /**
   * Schedules an event to retry to send the contention.
   */
  private def retryAfter(timeout: FiniteDuration,
                         to: ActorPath,
                         contention: Contention,
                         key: ContentionKey): SyncIO[Unit] =
    SyncIO(timers.startSingleTimer(key, SendContention(to, contention), timeout))

  /**
   * Send the contention to `to` expecting an ack. If an ack is not received in 1 second the actor
   * will retry.
   */
  private def sendContentionWithRetry(contention: Contention, to: ActorPath): Eval[Unit] =
    liftF(
      SyncIO(context.system.actorSelection(to) ! contention) >> retryAfter(
        1.second,
        to,
        contention,
        ContentionKey(to, contention.observer, contention.subject)
      )
    )

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
  type Eval[A] = StateT[SyncIO, SBFailureDetectorState, A]

  def props(sendTo: ActorRef): Props = Props(new SBFailureDetector(sendTo))

  sealed abstract class SBRReachability extends Product with Serializable
  final case object Reachable           extends SBRReachability
  final case object Unreachable         extends SBRReachability
  final case object IndirectlyConnected extends SBRReachability

  final case class Contention(protester: UniqueAddress, observer: Observer, subject: Subject, version: Version)

  /**
   * Acknowledgment of a contention message,
   *
   * Warning: `from` must containing the address!
   */
  final case class ContentionAck(from: ActorPath, observer: Observer, subject: Subject, version: Version)

  object ContentionAck {
    def fromContention(contention: Contention, from: ActorPath): ContentionAck =
      ContentionAck(from, contention.observer, contention.subject, contention.version)
  }

  /**
   * Key for the timer related to the at-least-once delivery resend for the contention
   * of the observation of `observer` of `subject` as unreachable.
   *
   * Warning: `to` must containing the address!
   */
  final case class ContentionKey(to: ActorPath, observer: Observer, subject: Subject)

  object ContentionKey {
    def fromAck(ack: ContentionAck): ContentionKey = ContentionKey(ack.from, ack.observer, ack.subject)
  }

  final case class SendContention(to: ActorPath, contention: Contention)

  sealed abstract private class LocalReachability
  private case object Unknown            extends LocalReachability
  private case object LocallyReachable   extends LocalReachability
  private case object LocallyUnreachable extends LocalReachability
}
