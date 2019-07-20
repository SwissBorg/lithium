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
import com.swissborg.sbr.reporter._
import com.swissborg.sbr.converter._

import scala.concurrent.duration._

/**
  * Actor reporting the reachability status of cluster members based on `akka.cluster.Reachability`.
  *
  * A node is indirectly connected when only some of its observers see it as unreachable.
  * This might happen for instance when the link between two nodes is faulty,
  * they cannot directly communicate but can via another node.
  *
  * @param splitBrainReporter the actor to which the reachability events have to be sent.
  */
private[sbr] class ReachabilityReporter(private val splitBrainReporter: ActorRef)
    extends Actor
    with ActorLogging
    with Stash
    with Timers {
  import ReachabilityReporter._

  private val cluster = Cluster(context.system)
  private val selfUniqueAddress = cluster.selfUniqueAddress
  private val failureDetector = cluster.failureDetector

  final private val ackTimeout: FiniteDuration = 1.second
  final private val maxRetries: Int = 10

  override def receive: Receive = initializing

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def initializing: Receive = {
    case snapshot: CurrentClusterState =>
      unstashAll()
      context.become(
        active(ReachabilityReporterState.fromSnapshot(snapshot, cluster.selfDataCenter))
      )

    case _ => stash()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def active(state: ReachabilityReporterState): Receive = {
    case SBReachabilityChanged(r) =>
      context.become(active(updateReachabilities(r).runS(state).unsafeRunSync()))

    case MemberJoined(m) =>
      context.become(active(add(m).runS(state).unsafeRunSync()))

    case MemberRemoved(m, _) =>
      context.become(active(remove(m.uniqueAddress).runS(state).unsafeRunSync()))

    case ReachableMember(m) =>
      context.become(active(withReachable(m.uniqueAddress).runS(state).unsafeRunSync()))

    case c: SuspiciousDetection =>
      context.become(active(withSuspiciousDetectionFrom(sender(), c).runS(state).unsafeRunSync()))

    case ack: SuspiciousDetectionAck =>
      context.become(active(registerSuspiciousDetectionAck(ack).runS(state).unsafeRunSync()))

    case SendWithRetry(message, to, key, timeout, maxRetries) =>
      context.become(
        active(sendWithRetry(message, to, key, timeout, maxRetries - 1).runS(state).unsafeRunSync())
      )
  }

  /**
    * Broadcast suspicious detections if the current node sees unreachable nodes as reachable.
    * Otherwise, send the updated reachability to the reporter.
    *
    * Sends the suspicious detections with at-least-once delivery semantics.
    */
  private def updateReachabilities(reachability: SBReachability): Res[Unit] = {

    /**
      * Attempts to find the record of `observer` that describes
      * `subject` as unreachable and return it as a suspicious detection.
      *
      * Assumes that there's only one record per observer, subject pair in the
      * `Reachability` data structure.
      */
    def unreachableRecord(
        reachability: SBReachability,
        observer: Observer,
        subject: Subject
    ): Option[SuspiciousDetection] =
      reachability
        .findUnreachableRecord(observer, subject)
        .map(r => SuspiciousDetection(selfUniqueAddress, r.observer, r.subject, r.version))

    def localReachability(node: UniqueAddress): Res[LocalReachability] =
      StateT.liftF(SyncIO {
        if (failureDetector.isMonitoring(node.address)) {
          if (failureDetector.isAvailable(node.address)) LocallyReachable
          else LocallyUnreachable
        } else Unknown
      })

    /**
      * Send the suspicious detection to `to` expecting an ack. If an ack is not received in 1 second the actor
      * will retry.
      */
    def sendSuspiciousDetectionWithRetry(
        suspiciousDetection: SuspiciousDetection,
        to: UniqueAddress,
        expectedAck: SuspiciousDetectionAck
    ): Res[Unit] =
      for {
        _ <- StateT.modify[SyncIO, ReachabilityReporterState](
          _.expectSuspiciousDetectionAck(expectedAck)
        )
        _ <- sendWithRetry(
          suspiciousDetection,
          sbReachabilityReporterOnNode(to),
          expectedAck,
          ackTimeout,
          maxRetries
        )
      } yield ()

    /**
      * All the instances of this actor living on the other cluster nodes.
      */
    val sbFailureDetectors: Res[List[UniqueAddress]] = StateT.liftF(
      SyncIO(cluster.state.members.toList.map(_.uniqueAddress))
    )

    /**
      * Broadcast with at-least-once delivery the suspicious detection to all the `SBFailureDetector`s
      * that exist on the cluster members, including itself.
      */
    def broadcastSuspiciousDetectionWithRetry(suspiciousDetection: SuspiciousDetection): Res[Unit] =
      for {
        sbFailureDetectors <- sbFailureDetectors
        state <- StateT.get[SyncIO, ReachabilityReporterState]
        state <- sbFailureDetectors.traverse_[Res, Unit] { to =>
          val expectedAck = SuspiciousDetectionAck.fromSuspiciousDetection(suspiciousDetection, to)

          val hasBeenAcked =
            state.receivedAcks.get(to).exists(_.exists(_ === expectedAck))

          val sendingInProgress =
            state.pendingSuspiciousDetectionAcks.get(to).exists(_.exists(_ === expectedAck))

          val res: StateT[SyncIO, ReachabilityReporterState, Unit] =
            if (hasBeenAcked || sendingInProgress) {
              // No need to send the broadcast as it was already received.
              StateT.empty
            } else if (to === selfUniqueAddress) {
              // Shortcut
              for {
                _ <- StateT.modify[SyncIO, ReachabilityReporterState] {
                  _.withSuspiciousDetection(
                    suspiciousDetection.protester,
                    suspiciousDetection.observer,
                    suspiciousDetection.subject,
                    suspiciousDetection.version
                  ).registerSuspiciousDetectionAck(expectedAck) // so it won't be done again in subsequently
                }

                _ <- sendReachabilityIfChanged(suspiciousDetection.subject)
              } yield ()

            } else {
              for {
                // Cancel the timer for the previous observer, subject pair suspicious detection
                // as there is only one timer per such pair. There's no need to make sure
                // it was delivered, the new suspicious detection will override it.
                _ <- StateT.liftF(cancelSuspiciousDetectionResend(expectedAck))
                _ <- sendSuspiciousDetectionWithRetry(suspiciousDetection, to, expectedAck)
              } yield ()
            }

          res
        }
      } yield state

    // TODO directly use the record
    def withUnreachableFrom(suspiciousDetection: SuspiciousDetection): Res[Unit] =
      StateT.modify(
        _.withUnreachableFrom(
          suspiciousDetection.observer,
          suspiciousDetection.subject,
          suspiciousDetection.version
        )
      )

    /**
      * Remove from the state all the suspicious detections on unreachability detections
      * that are not part of the state anymore.
      */
    def removeStaleSuspiciousDetections(reachability: SBReachability): Res[Unit] = StateT.modify {
      state =>
        state.receivedAcks.valuesIterator.flatten
          .filterNot {
            case SuspiciousDetectionAck(_, observer, subject, version) =>
              reachability.findUnreachableRecord(observer, subject).exists(_.version == version)
          }
          .foldLeft(state) {
            case (state, SuspiciousDetectionAck(from, observer, subject, version)) =>
              state.withoutSuspiciousDetection(from, observer, subject, version)
          }
    }

    def updateReachabilityStatuses(reachability: SBReachability): Res[Unit] =
      reachability.observersGroupedByUnreachable.toList
        .traverse_ {
          case (subject, observers) =>
            observers.toList.traverse_[Res, Unit] { observer =>
              for {
                _ <- localReachability(subject).flatMap {
                  case LocallyReachable =>
                    unreachableRecord(reachability, observer, subject).traverse_(
                      broadcastSuspiciousDetectionWithRetry
                    )

                  case LocallyUnreachable | Unknown =>
                    unreachableRecord(reachability, observer, subject).traverse_(
                      withUnreachableFrom
                    )
                }
                _ <- sendReachabilityIfChanged(subject)
              } yield ()
            }
        }

    for {
      selfDcReachability <- StateT
        .get[SyncIO, ReachabilityReporterState]
        .map(s => reachability.remove(s.otherDcMembers))
      _ <- removeStaleSuspiciousDetections(selfDcReachability)
      _ <- updateReachabilityStatuses(selfDcReachability)
    } yield ()
  }

  private def add(member: Member): Res[Unit] = StateT.modify(_.add(member))

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
      val cancelSuspiciousDetectionResend0: Res[Unit] = StateT.inspectF {
        _.pendingSuspiciousDetectionAcks
          .getOrElse(node, Set.empty)
          .foldLeft(SyncIO.unit) {
            case (cancelSuspiciousDetectionResends, ack) =>
              cancelSuspiciousDetectionResends >> cancelSuspiciousDetectionResend(ack)
          }
      }

      for {
        _ <- cancelSuspiciousDetectionResend0
        _ <- StateT.modify[SyncIO, ReachabilityReporterState](_.remove(node))
        _ <- sendAllChangedReachabilities
      } yield ()
    }

  /**
    * Register the node as reachable and inform the reporter of it.
    */
  private def withReachable(node: UniqueAddress): Res[Unit] =
    for {
      _ <- StateT.modify[SyncIO, ReachabilityReporterState](_.withReachable(node))
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
      status <- ReachabilityReporterState.updatedReachability(subject).mapK(evalToSyncIO)
      _ <- StateT.liftF(status.traverse_(sendReachability(subject, _)))
    } yield ()

  /**
    * Send the reachability of `subject` to the reporter.
    *
    * If it is the same as the previous time this function was called
    * it will do nothing.
    */
  private val sendAllChangedReachabilities: Res[Unit] = for {
    statuses <- ReachabilityReporterState.allUpdatedReachabilies.mapK(evalToSyncIO)
    _ <- StateT.liftF(statuses.toList.traverse_ { case (k, v) => sendReachability(k, v) })
  } yield ()

  private def sendReachability(subject: Subject, reachability: ReachabilityStatus): SyncIO[Unit] =
    SyncIO(splitBrainReporter ! (reachability match {
      case ReachabilityStatus.Reachable =>
        SplitBrainReporter.NodeReachable(subject)

      case ReachabilityStatus.IndirectlyConnected =>
        SplitBrainReporter.NodeIndirectlyConnected(subject)

      case ReachabilityStatus.Unreachable =>
        SplitBrainReporter.NodeUnreachable(subject)
    }))

  /**
    * Add the suspicious detection and acknowledge the sender that it was received.
    */
  private def withSuspiciousDetectionFrom(
      sender: ActorRef,
      suspiciousDetection: SuspiciousDetection
  ): Res[Unit] = {
    def withSuspiciousDetection(suspiciousDetection: SuspiciousDetection): Res[Unit] =
      StateT.modify(
        _.withSuspiciousDetection(
          suspiciousDetection.protester,
          suspiciousDetection.observer,
          suspiciousDetection.subject,
          suspiciousDetection.version
        )
      )

    def ackSuspiciousDetection(
        sender: ActorRef,
        suspiciousDetection: SuspiciousDetection
    ): Res[Unit] =
      StateT.liftF(
        SyncIO(
          sender ! SuspiciousDetectionAck
            .fromSuspiciousDetection(suspiciousDetection, selfUniqueAddress)
        )
      )

    for {
      _ <- StateT.liftF(SyncIO(log.debug("Received suspicious detection: {}", suspiciousDetection)))
      _ <- withSuspiciousDetection(suspiciousDetection)
      _ <- sendReachabilityIfChanged(suspiciousDetection.subject)
      _ <- ackSuspiciousDetection(sender, suspiciousDetection)
    } yield ()
  }

  private def registerSuspiciousDetectionAck(ack: SuspiciousDetectionAck): Res[Unit] =
    for {
      _ <- StateT.liftF(SyncIO(log.debug("Received suspicious detection ack: {}", ack)))
      _ <- StateT.liftF(cancelSuspiciousDetectionResend(ack))
      _ <- StateT.modify[SyncIO, ReachabilityReporterState](_.registerSuspiciousDetectionAck(ack))
    } yield ()

  /**
    * Send `message` to `to` every second until an ack is received.
    */
  private def sendWithRetry[M, K](
      message: M,
      to: ActorPath,
      cancellationKey: K,
      timeout: FiniteDuration,
      maxRetries: Int
  ): Res[Unit] = {
    val retryIfNotAcked: SyncIO[Unit] =
      SyncIO(
        timers.startSingleTimer(
          cancellationKey,
          SendWithRetry(message, to, cancellationKey, timeout, maxRetries),
          timeout
        )
      )

    StateT.liftF {
      if (maxRetries < 0) {
        SyncIO(
          log
            .warning("Failed to send {} to {} after {} attempts!", message, to, this.maxRetries + 1)
        )
      } else {
        for {
          _ <- SyncIO(context.system.actorSelection(to) ! message)
          _ <- SyncIO(
            log.debug(
              "Attempting to send {} to {} (Attempt {}/{})",
              message,
              to,
              this.maxRetries - maxRetries + 1,
              this.maxRetries + 1
            )
          )
          _ <- retryIfNotAcked
        } yield ()
      }
    }
  }

  private def cancelSuspiciousDetectionResend(ack: SuspiciousDetectionAck): SyncIO[Unit] =
    SyncIO(timers.cancel(ack))

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

private[sbr] object ReachabilityReporter {
  private type Res[A] = StateT[SyncIO, ReachabilityReporterState, A]

  def props(sendTo: ActorRef): Props = Props(new ReachabilityReporter(sendTo))

  /**
    * Send `message` to `addressee`. If the message is not acknowledged it is resent after `timeout`. The retry can
    * be cancelled using the timer `key`.
    */
  private final case class SendWithRetry[M, K](
      message: M,
      addressee: ActorPath,
      key: K,
      timeout: FiniteDuration,
      maxRetries: Int
  )

  sealed abstract private class LocalReachability
  private case object Unknown extends LocalReachability
  private case object LocallyReachable extends LocalReachability
  private case object LocallyUnreachable extends LocalReachability

  /**
    * Acknowledgment of a suspicious detection message,
    *
    * Warning: `from` must containing the address!
    */
  final case class SuspiciousDetectionAck(
      from: UniqueAddress,
      observer: Observer,
      subject: Subject,
      version: Version
  )

  object SuspiciousDetectionAck {
    def fromSuspiciousDetection(
        suspiciousDetection: SuspiciousDetection,
        from: UniqueAddress
    ): SuspiciousDetectionAck =
      SuspiciousDetectionAck(
        from,
        suspiciousDetection.observer,
        suspiciousDetection.subject,
        suspiciousDetection.version
      )

    implicit val suspiciousDetectionAckEq
        : Eq[SuspiciousDetectionAck] = (x: SuspiciousDetectionAck, y: SuspiciousDetectionAck) =>
      x.from === y.from && x.observer === y.observer && x.subject === y.subject && x.version === y.version
  }

  final case class SuspiciousDetection(
      protester: UniqueAddress,
      observer: Observer,
      subject: Subject,
      version: Version
  )

  private val evalToSyncIO: Eval ~> SyncIO = new ~>[Eval, SyncIO] {
    override def apply[A](fa: Eval[A]): SyncIO[A] = SyncIO.eval(fa)
  }
}
