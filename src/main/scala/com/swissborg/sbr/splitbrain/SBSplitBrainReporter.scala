package com.swissborg.sbr.splitbrain

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Stash, Timers}
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus.{Joining, WeaklyUp}
import akka.cluster.{Cluster, Member}
import cats.data.StateT
import cats.data.StateT._
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.reachability.SBReachabilityReporter
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.resolver.SBResolver

import scala.concurrent.duration._

/**
 * Actor reporting on split-brain events.
 *
 * @param splitBrainResolver the actor that resolves the split-brain scenarios.
 * @param stableAfter duration during which a cluster has to be stable before attempting to resolve a split-brain.
 */
class SBSplitBrainReporter(splitBrainResolver: ActorRef, stableAfter: FiniteDuration, downAllWhenUnstable: Boolean)
    extends Actor
    with Stash
    with ActorLogging
    with Timers {
  import SBSplitBrainReporter._

  private val cluster    = Cluster(context.system)
  private val selfMember = cluster.selfMember

  context.actorOf(SBReachabilityReporter.props(self), "sbr-fd")

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case snapshot: CurrentClusterState =>
      unstashAll()
      context.become(active(SBSplitBrainReporterState.fromSnapshot(selfMember, snapshot)))

    case _ => stash()
  }

  private def active(state: SBSplitBrainReporterState): Receive = {
    case e: MemberEvent =>
      context.become(active(enqueue(e).runS(state).unsafeRunSync()))

    case SBSeenChanged(convergence, seenBy) =>
      context.become(active(consumeQueue(convergence, seenBy).runS(state).unsafeRunSync()))

    case ReachableMember(m) =>
      context.become(active(withReachableMember(m).runS(state).unsafeRunSync()))

    case UnreachableMember(m) =>
      context.become(active(withUnreachableMember(m).runS(state).unsafeRunSync()))

    case IndirectlyConnectedMember(m) =>
      context.become(active(withIndirectlyConnectedMember(m).runS(state).unsafeRunSync()))

    case ClusterIsStable =>
      context.become(active(handleSplitBrain.runS(state).unsafeRunSync()))

    case ClusterIsUnstable =>
      context.become(active(downAll.runS(state).unsafeRunSync()))
  }

  /**
   * Update the changes described by the change queue and prune
   * the removed members if the membership converged.
   */
  private def consumeQueue(convergence: Boolean, seenBy: Set[Address]): Eval[Unit] =
    if (convergence) {
      modifyS(_.consumeQueue(seenBy).pruneRemoved)
    } else {
      modifyS(_.consumeQueue(seenBy))
    }

  /**
   * Modify the state using `f` and TODO
   */
  private def modifyS(f: SBSplitBrainReporterState => SBSplitBrainReporterState): Eval[Unit] = modifyF { state =>
    val updatedState = f(state)

    val diff = DiffInfo(state.worldView, updatedState.worldView)

    // Cancel the `ClusterIsUnstable` event when
    // the split-brain has worsened.
    def cancelClusterIsUnstableIfSplitBrainResolved: SyncIO[Unit] =
      if (hasSplitBrain(state.worldView)) SyncIO.unit
      else cancelClusterIsUnstable

    // Schedule the `ClusterIsUnstable` event if
    // a split-brain has appeared. This way
    // it doesn't get rescheduled for problematic
    // nodes that are being downed.
    def scheduleClusterIsUnstableIfSplitBrainWorsened: SyncIO[Unit] =
      if (diff.hasNewUnreachableOrIndirectlyConnected) scheduleClusterIsUnstable
      else SyncIO.unit

    // Reset `ClusterIsStable` if the modification is not stable.
    val resetClusterIsStableIfUnstable: SyncIO[Unit] =
      if (diff.changeIsStable) SyncIO.unit
      else resetClusterIsStable

    for {
      // Run `ClusterIsUnstable` timer only when needed. If the timer is running
      // while deactivated it will interfere with the `ClusterIsStable` and
      // cancel it gets triggered.
      _ <- if (downAllWhenUnstable)
        clusterIsUnstableIsActive.ifM(cancelClusterIsUnstableIfSplitBrainResolved,
                                      scheduleClusterIsUnstableIfSplitBrainWorsened)
      else SyncIO.unit

      _ <- resetClusterIsStableIfUnstable
    } yield updatedState
  }

  private def enqueue(e: MemberEvent): Eval[Unit] =
    modifyS(_.enqueue(e))

  private def withReachableMember(m: Member): Eval[Unit] =
    modifyS(_.withReachableMember(m)) >> liftF(SyncIO(log.debug("withReachableMember({})", m)))

  private def withUnreachableMember(m: Member): Eval[Unit] =
    modifyS(_.withUnreachableMember(m)) >> liftF(SyncIO(log.debug("withUnreachableMember({})", m)))

  private def withIndirectlyConnectedMember(m: Member): Eval[Unit] =
    modifyS(_.withIndirectlyConnectedMember(m)) >> liftF(SyncIO(log.debug("withIndirectlyConnectedMember({})", m)))

  private val scheduleClusterIsStable: SyncIO[Unit] =
    SyncIO(timers.startSingleTimer(ClusterIsStable, ClusterIsStable, stableAfter)) >> SyncIO(
      log.debug("START STABLE")
    )

  private val cancelClusterIsStable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsStable))

  private val resetClusterIsStable: SyncIO[Unit] = cancelClusterIsStable >> scheduleClusterIsStable >> SyncIO(
    log.debug("RESET STABLE")
  )

  private val scheduleClusterIsUnstable: SyncIO[Unit] =
    SyncIO(
      timers
        .startSingleTimer(ClusterIsUnstable, ClusterIsUnstable, stableAfter + ((stableAfter.toMillis * 0.75) millis))
    ) >> SyncIO(log.debug("SCHEDULE UNSTABLE"))

  private val cancelClusterIsUnstable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsUnstable)) >> SyncIO(
    log.debug("CANCEL UNSTABLE")
  )

  private val clusterIsUnstableIsActive: SyncIO[Boolean] = SyncIO(timers.isTimerActive(ClusterIsUnstable))

  /**
   * Send the resolver the order to run a split-brain resolution.
   *
   * If there's not split-brain, does nothing.
   */
  private val handleSplitBrain: Eval[Unit] =
    for {
      _ <- liftF[SyncIO, SBSplitBrainReporterState, Unit](cancelClusterIsUnstable)
      _ <- ifSplitBrain(SBResolver.HandleSplitBrain(_))
      _ <- liftF(scheduleClusterIsStable)
    } yield ()

  private val downAll: Eval[Unit] = for {
    _ <- liftF[SyncIO, SBSplitBrainReporterState, Unit](cancelClusterIsStable)
    _ <- ifSplitBrain(SBResolver.DownAll)
  } yield ()

  private def ifSplitBrain(event: WorldView => SBResolver.Event): Eval[Unit] =
    inspectF { state =>
      if (hasSplitBrain(state.worldView)) {
        SyncIO(splitBrainResolver ! event(state.worldView))
      } else {
        SyncIO.unit
      }
    }

  private def hasSplitBrain(worldView: WorldView): Boolean =
    worldView.unreachableNodes.nonEmpty || worldView.indirectlyConnectedNodes.nonEmpty

  override def preStart(): Unit = {
    cluster.subscribe(self, InitialStateAsSnapshot, classOf[akka.cluster.ClusterEvent.MemberEvent])
    Converter(context.system).subscribeToSeenChanged(self)
    scheduleClusterIsStable.unsafeRunSync()
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    Converter(context.system).unsubscribe(self)
    timers.cancel(ClusterIsStable)
  }
}

object SBSplitBrainReporter {
  private type Eval[A] = StateT[SyncIO, SBSplitBrainReporterState, A]

  def props(downer: ActorRef, stableAfter: FiniteDuration, downAllWhenUnstable: Boolean): Props =
    Props(new SBSplitBrainReporter(downer, stableAfter, downAllWhenUnstable))

  final private case object ClusterIsStable
  final private case object ClusterIsUnstable
  final case class IndirectlyConnectedMember(member: Member)

  /**
   * Information on the difference between two world views.
   *
   * @param changeIsStable true if both world views are the same ignoring `Joining` and `WeaklyUp` members.
   * @param hasNewUnreachableOrIndirectlyConnected true if the updated world view has more unreachable or indirectly
   *                                               connected nodes.
   */
  sealed abstract case class DiffInfo(changeIsStable: Boolean, hasNewUnreachableOrIndirectlyConnected: Boolean)

  private[sbr] object DiffInfo {
    def apply(oldWorldView: WorldView, updatedWorldView: WorldView): DiffInfo = {
      // Remove members that are `Joining` or `WeaklyUp` as they
      // can appear during a split-brain.
      def nonJoining[N <: Node](nodes: Set[N]): List[Member] =
        nodes.iterator.collect {
          case ReachableNode(member) if member.status =!= Joining && member.status =!= WeaklyUp           => member
          case UnreachableNode(member) if member.status =!= Joining && member.status =!= WeaklyUp         => member
          case IndirectlyConnectedNode(member) if member.status =!= Joining && member.status =!= WeaklyUp => member
        }.toList

      /**
       * True if the both lists contain the same members in the same order.
       *
       * Warning: expects both arguments to be sorted.
       */
      def pairWiseEquals(members1: List[Member], members2: List[Member]): Boolean =
        members1.sorted.zip(members2.sorted).forall {
          case (member1, member2) =>
            member1 === member2 && // only compares unique addresses
              member1.status === member2.status
        }

      val oldReachable           = nonJoining(oldWorldView.reachableNodes)
      val oldIndirectlyConnected = nonJoining(oldWorldView.indirectlyConnectedNodes)
      val oldUnreachable         = nonJoining(oldWorldView.unreachableNodes)

      val updatedReachable           = nonJoining(updatedWorldView.reachableNodes)
      val updatedIndirectlyConnected = nonJoining(updatedWorldView.indirectlyConnectedNodes)
      val updatedUnreachable         = nonJoining(updatedWorldView.unreachableNodes)

      val stable = pairWiseEquals(oldReachable, updatedReachable) &&
        // A change between unreachable and indirectly-connected does not affect the stability.
        pairWiseEquals(oldIndirectlyConnected ++ oldUnreachable, updatedIndirectlyConnected ++ updatedUnreachable)

      val increase =
        oldIndirectlyConnected.size < updatedIndirectlyConnected.size || oldUnreachable.size < updatedUnreachable.size

      new DiffInfo(stable, increase) {}
    }
  }
}
