package com.swissborg.sbr
package reachability

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster._
import akka.cluster.swissborg._
import cats.data._
import cats.effect._
import cats.implicits._
import cats._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.splitbrain._
import com.swissborg.sbr.converter._

import scala.concurrent.duration._

/**
  * Actor reporting the reachability status of cluster members based on `akka.cluster.Reachability`.
  *
  * A node is indirectly connected when only some of its observers see it as unreachable.
  * This might happen for instance when the link between two nodes is faulty,
  * they cannot directly communicate but can via another node.
  *
  * @param sbSplitBrainReporter the actor to which the reachability events have to be sent.
  */
private[sbr] class SBReachabilityReporter(private val sbSplitBrainReporter: ActorRef)
    extends Actor
    with ActorLogging
    with Stash
    with Timers {
  import SBReachabilityReporter._

  private val cluster = Cluster(context.system)
  private val selfUniqueAddress = cluster.selfUniqueAddress
  private val failureDetector = cluster.failureDetector

  final private val ackTimeout: FiniteDuration = 1.second

  override def receive: Receive = initializing

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def initializing: Receive = {
    case _: CurrentClusterState =>
      unstashAll()
      context.become(active(SBReachabilityReporterState(selfUniqueAddress)))

    case _ => stash()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def active(state: SBReachabilityReporterState): Receive = {
    case SBReachabilityChanged(r) =>
      context.become(active(updateReachabilities(r).runS(state).unsafeRunSync()))

    case MemberRemoved(m, _) =>
      context.become(active(remove(m.uniqueAddress).runS(state).unsafeRunSync()))

    case ReachableMember(m) =>
      context.become(active(withReachable(m.uniqueAddress).runS(state).unsafeRunSync()))

    case c: Contention =>
      context.become(active(withContentionFrom(sender(), c).runS(state).unsafeRunSync()))

    case ack: ContentionAck =>
      context.become(active(registerContentionAck(ack).runS(state).unsafeRunSync()))

    case SendWithRetry(message, to, key, timeout) =>
      context.become(active(sendWithRetry(message, to, key, timeout).runS(state).unsafeRunSync()))
  }

  /**
    * Broadcast contentions if the current node sees unreachable nodes as reachable.
    * Otherwise, send the updated reachability to the reporter.
    *
    * Sends the contentions with at-least-once delivery semantics.
    */
  private def updateReachabilities(reachability: SBReachability): Res[Unit] = {

    /**
      * Attempts to find the record of `observer` that describes
      * `subject` as unreachable.
      *
      * Assumes that there's only one record per observer, subject pair in the
      * `Reachability` data structure.
      */
    def unreachableRecord(observer: Observer, subject: Subject): Option[Contention] =
      reachability
        .findUnreachableRecord(observer, subject)
        .map(r => Contention(selfUniqueAddress, r.observer, r.subject, r.version))

    def localReachability(node: UniqueAddress): Res[LocalReachability] =
      StateT.liftF(SyncIO {
        if (failureDetector.isMonitoring(node.address)) {
          if (failureDetector.isAvailable(node.address)) LocallyReachable
          else LocallyUnreachable
        } else Unknown
      })

    /**
      * Send the contention to `to` expecting an ack. If an ack is not received in 1 second the actor
      * will retry.
      */
    def sendContentionWithRetry(
        contention: Contention,
        to: UniqueAddress,
        expectedAck: ContentionAck
    ): Res[Unit] =
      for {
        _ <- StateT.modify[SyncIO, SBReachabilityReporterState](_.expectContentionAck(expectedAck))
        _ <- sendWithRetry(contention, sbReachabilityReporterOnNode(to), expectedAck, ackTimeout)
      } yield ()

    /**
      * All the instances of this actor living on the other cluster nodes.
      */
    val sbFailureDetectors: Res[List[UniqueAddress]] = StateT.liftF(
      SyncIO(cluster.state.members.toList.map(_.uniqueAddress))
    )

    /**
      * Broadcast with at-least-once delivery the contention to all the `SBFailureDetector`s
      * that exist on the cluster members, including itself.
      */
    def broadcastContentionWithRetry(contention: Contention): Res[Unit] =
      for {
        sbFailureDetectors <- sbFailureDetectors
        state <- StateT.get[SyncIO, SBReachabilityReporterState]
        state <- sbFailureDetectors.traverse_[Res, Unit] { to =>
          val expectedAck = ContentionAck.fromContention(contention, to)

          val hasBeenAcked =
            state.receivedAcks.get(to).exists(_.exists(_ === expectedAck))

          val sendingInProgress =
            state.pendingContentionAcks.get(to).exists(_.exists(_ === expectedAck))

          val res: StateT[SyncIO, SBReachabilityReporterState, Unit] =
            if (hasBeenAcked || sendingInProgress) {
              // No need to send the broadcast as it was already received.
              StateT.empty
            } else if (to === selfUniqueAddress) {
              // Shortcut
              for {
                _ <- StateT.modify[SyncIO, SBReachabilityReporterState] {
                  _.withContention(
                    contention.protester,
                    contention.observer,
                    contention.subject,
                    contention.version
                  ).registerContentionAck(expectedAck) // so it won't be done again in subsequently
                }

                _ <- sendReachabilityIfChanged(contention.subject)
              } yield ()

            } else {
              for {
                // Cancel the timer for the previous observer, subject pair contention
                // as there is only one timer per such pair. There's no need to make sure
                // it was delivered, the new contention will override it.
                _ <- StateT.liftF(cancelContentionResend(expectedAck))
                _ <- sendContentionWithRetry(contention, to, expectedAck)
              } yield ()
            }

          res
        }
      } yield state

    // TODO directly use the record
    def withUnreachableFrom(contention: Contention): Res[Unit] =
      StateT.modify(
        _.withUnreachableFrom(contention.observer, contention.subject, contention.version)
      )

    /**
      * Remove from the state all the contentions on unreachability detections
      * that are not part of the state anymore.
      */
    def removeStaleContentions(reachability: SBReachability): Res[Unit] = StateT.modify { state =>
      state.receivedAcks.valuesIterator.flatten
        .filterNot {
          case ContentionAck(_, observer, subject, version) =>
            reachability.findUnreachableRecord(observer, subject).exists(_.version == version)
        }
        .foldLeft(state) {
          case (state, ContentionAck(from, observer, subject, version)) =>
            state.withoutContention(from, observer, subject, version)
        }
    }

    reachability.observersGroupedByUnreachable.toList
      .traverse_ {
        case (subject, observers) =>
          observers.toList.traverse_[Res, Unit] { observer =>
            for {
              _ <- removeStaleContentions(reachability)
              _ <- localReachability(subject).flatMap {
                case LocallyReachable =>
                  unreachableRecord(observer, subject).traverse_(broadcastContentionWithRetry)
                case LocallyUnreachable | Unknown =>
                  unreachableRecord(observer, subject).traverse_(withUnreachableFrom)
              }
              _ <- sendReachabilityIfChanged(subject)
            } yield ()
          }
      }
  }

  /**
    * Register the node as removed.
    *
    * If the removed node is the current one the actor will stop itself.
    */
  private def remove(node: UniqueAddress): Res[Unit] =
    if (node === selfUniqueAddress) {
      // This node is being stopped. Kill the actor
      // to stop any further updates.
      StateT.liftF(SyncIO(context.stop(self)))
    } else {
      val cancelContentionResend0: Res[Unit] = StateT.inspectF {
        _.pendingContentionAcks
          .getOrElse(node, Set.empty)
          .foldLeft(SyncIO.unit) {
            case (cancelContentionResends, ack) =>
              cancelContentionResends >> cancelContentionResend(ack)
          }
      }

      for {
        _ <- cancelContentionResend0
        _ <- StateT.modify[SyncIO, SBReachabilityReporterState](_.remove(node))
        _ <- sendAllChangedReachabilities
      } yield ()
    }

  /**
    * Register the node as reachable and inform the reporter of it.
    */
  private def withReachable(node: UniqueAddress): Res[Unit] =
    for {
      _ <- StateT.modify[SyncIO, SBReachabilityReporterState](_.withReachable(node))
      _ <- sendReachabilityIfChanged(node)
    } yield ()

  /**
    * Send the reachability of `subject` to the reporter.
    *
    * If it is the same as the previous time this function was called
    * it will do nothing.
    */
  private def sendReachabilityIfChanged(subject: Subject): Res[Unit] =
    for {
      status <- SBReachabilityReporterState.updatedReachability(subject).mapK(evalToSyncIO)
      _ <- StateT.liftF(status.traverse_(sendReachability(subject, _)))
    } yield ()

  /**
    * Send the reachability of `subject` to the reporter.
    *
    * If it is the same as the previous time this function was called
    * it will do nothing.
    */
  private val sendAllChangedReachabilities: Res[Unit] = for {
    statuses <- SBReachabilityReporterState.allUpdatedReachabilies.mapK(evalToSyncIO)
    _ <- StateT.liftF(statuses.toList.traverse_ { case (k, v) => sendReachability(k, v) })
  } yield ()

  private def sendReachability(subject: Subject, reachability: SBReachabilityStatus): SyncIO[Unit] =
    SyncIO(sbSplitBrainReporter ! (reachability match {
      case SBReachabilityStatus.Reachable =>
        SBSplitBrainReporter.NodeReachable(subject)

      case SBReachabilityStatus.IndirectlyConnected =>
        SBSplitBrainReporter.NodeIndirectlyConnected(subject)

      case SBReachabilityStatus.Unreachable =>
        SBSplitBrainReporter.NodeUnreachable(subject)
    }))

  /**
    * Add the contention and acknowledge the sender that it was received.
    */
  private def withContentionFrom(sender: ActorRef, contention: Contention): Res[Unit] = {
    def withContention(contention: Contention): Res[Unit] = StateT.modify(
      _.withContention(
        contention.protester,
        contention.observer,
        contention.subject,
        contention.version
      )
    )

    def ackContention(sender: ActorRef, contention: Contention): Res[Unit] = StateT.liftF(
      SyncIO(sender ! ContentionAck.fromContention(contention, selfUniqueAddress))
    )

    for {
      _ <- StateT.liftF(SyncIO(log.debug("Received contention: {}", contention)))
      _ <- withContention(contention)
      _ <- sendReachabilityIfChanged(contention.subject)
      _ <- ackContention(sender, contention)
    } yield ()
  }

  private def registerContentionAck(ack: ContentionAck): Res[Unit] =
    for {
      _ <- StateT.liftF(SyncIO(log.debug("Received contention ack: {}", ack)))
      _ <- StateT.liftF(cancelContentionResend(ack))
      _ <- StateT.modify[SyncIO, SBReachabilityReporterState](_.registerContentionAck(ack))
    } yield ()

  /**
    * Send `message` to `to` every second until an ack is received.
    */
  private def sendWithRetry[M, K](
      message: M,
      to: ActorPath,
      cancellationKey: K,
      timeout: FiniteDuration
  ): Res[Unit] = {
    def retryAfter(timeout: FiniteDuration): SyncIO[Unit] =
      SyncIO(
        timers.startSingleTimer(
          cancellationKey,
          SendWithRetry(message, to, cancellationKey, timeout),
          timeout
        )
      )

    StateT.liftF(
      for {
        _ <- SyncIO(context.system.actorSelection(to) ! message)
        _ <- SyncIO(log.debug("Attempting to send {} to {}", message, to))
        _ <- retryAfter(timeout)
      } yield ()
    )
  }

  private def cancelContentionResend(ack: ContentionAck): SyncIO[Unit] = SyncIO(timers.cancel(ack))

  private def sbReachabilityReporterOnNode(node: UniqueAddress): ActorPath =
    ActorPath.fromString(s"${node.address.toString}/${self.path.toStringWithoutAddress}")

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
    discard(Converter(context.system).subscribeToReachabilityChanged(self))
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    Converter(context.system).unsubscribe(self)
    timers.cancelAll()
  }
}

private[sbr] object SBReachabilityReporter {
  private type Res[A] = StateT[SyncIO, SBReachabilityReporterState, A]

  def props(sendTo: ActorRef): Props = Props(new SBReachabilityReporter(sendTo))

  /**
    * Send `message` to `addressee`. If the message is not acknowledged it is resent after `timeout`. The retry can
    * be cancelled using the timer `key`.
    */
  private final case class SendWithRetry[M, K](
      message: M,
      addressee: ActorPath,
      key: K,
      timeout: FiniteDuration
  )

  sealed abstract private class LocalReachability
  private case object Unknown extends LocalReachability
  private case object LocallyReachable extends LocalReachability
  private case object LocallyUnreachable extends LocalReachability

  /**
    * Acknowledgment of a contention message,
    *
    * Warning: `from` must containing the address!
    */
  final case class ContentionAck(
      from: UniqueAddress,
      observer: Observer,
      subject: Subject,
      version: Version
  )

  object ContentionAck {
    def fromContention(contention: Contention, from: UniqueAddress): ContentionAck =
      ContentionAck(from, contention.observer, contention.subject, contention.version)

    implicit val contentionAckEq: Eq[ContentionAck] = (x: ContentionAck, y: ContentionAck) =>
      x.from === y.from && x.observer === y.observer && x.subject === y.subject && x.version === y.version
  }

  final case class Contention(
      protester: UniqueAddress,
      observer: Observer,
      subject: Subject,
      version: Version
  )

  private val evalToSyncIO: Eval ~> SyncIO = new ~>[Eval, SyncIO] {
    override def apply[A](fa: Eval[A]): SyncIO[A] = SyncIO.eval(fa)
  }
}
