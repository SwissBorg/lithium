package com.swissborg.sbr.reporter

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Stash, Timers}
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus.{Joining, WeaklyUp}
import akka.cluster.{Cluster, Member}
import cats.data.StateT
import cats.data.StateT._
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr.failuredetector.SBFailureDetector
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.resolver.SBResolver
import com.swissborg.sbr._

import scala.concurrent.duration._

/**
 * Actor reporting on split-brain events.
 *
 * @param splitBrainResolver the actor that resolves the split-brain scenarios.
 * @param stableAfter duration during which a cluster has to be stable before attempting to resolve a split-brain.
 */
class SBReporter(splitBrainResolver: ActorRef, stableAfter: FiniteDuration)
    extends Actor
    with Stash
    with ActorLogging
    with Timers {
  import SBReporter._

  private val cluster    = Cluster(context.system)
  private val selfMember = cluster.selfMember

  context.actorOf(SBFailureDetector.props(self), "sbr-fd")

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case snapshot: CurrentClusterState =>
      unstashAll()
      context.become(active(SBReporterState.fromSnapshot(selfMember, snapshot)))

    case _ => stash()
  }

  private def active(state: SBReporterState): Receive = {
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
  private def modifyS(f: SBReporterState => SBReporterState): Eval[Unit] = modifyF { state =>
    val updatedState = f(state)

    val ongoingSplitBrain = hasSplitBrain(state.worldView)
    val diff              = DiffInfo(state.worldView, updatedState.worldView)

    // Cancel the `ClusterIsUnstable` event when
    // the split-brain has worsened.
    val cancelClusterIsUnstableWhenSplitBrainResolved =
      if (ongoingSplitBrain) SyncIO.unit
      else cancelClusterIsUnstable

    // Schedule the `ClusterIsUnstable` event if
    // a split-brain has appeared. This way
    // it doesn't get rescheduled for problematic
    // nodes that are being downed.
    val scheduleClusterIsUnstableWhenSplitBrainWorsened =
      if (diff.hasNewUnreachableOrIndirectlyConnected) scheduleClusterIsUnstable
      else SyncIO.unit

    val resetClusterIsStableWhenUnstable =
      if (diff.changeIsStable) SyncIO.unit
      else resetClusterIsStable

    for {
      _ <- clusterIsUnstableIsActive.ifM(cancelClusterIsUnstableWhenSplitBrainResolved,
                                         scheduleClusterIsUnstableWhenSplitBrainWorsened)
      _ <- resetClusterIsStableWhenUnstable
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
    SyncIO(timers.startSingleTimer(ClusterIsStable, ClusterIsStable, stableAfter))

  private val cancelClusterIsStable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsStable))

  private val resetClusterIsStable: SyncIO[Unit] = cancelClusterIsStable >> scheduleClusterIsStable

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
      _ <- liftF[SyncIO, SBReporterState, Unit](cancelClusterIsUnstable)
      _ <- liftF[SyncIO, SBReporterState, Unit](
        clusterIsUnstableIsActive.flatMap(b => SyncIO(log.debug("ACTIVE {}", b)))
      )
      _ <- ifSplitBrain(SBResolver.HandleSplitBrain(_))
    } yield ()

  private val downAll: Eval[Unit] = for {
    _ <- liftF[SyncIO, SBReporterState, Unit](cancelClusterIsStable)
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
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    Converter(context.system).unsubscribe(self)
    timers.cancel(ClusterIsStable)
  }
}

object SBReporter {
  private type Eval[A] = StateT[SyncIO, SBReporterState, A]

  def props(downer: ActorRef, stableAfter: FiniteDuration): Props = Props(new SBReporter(downer, stableAfter))

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

  object DiffInfo {
    def apply(oldWorldView: WorldView, updatedWorldView: WorldView): DiffInfo = {
      // Joining and WeaklyUp nodes are counted in the diff as they can appear during a split-brain.
      def nonJoining[N <: Node](nodes: Set[N]): Set[Member] =
        nodes.iterator.collect {
          case ReachableNode(member) if member.status =!= Joining && member.status =!= WeaklyUp           => member
          case UnreachableNode(member) if member.status =!= Joining && member.status =!= WeaklyUp         => member
          case IndirectlyConnectedNode(member) if member.status =!= Joining && member.status =!= WeaklyUp => member
        }.toSet

      val oldReachable           = nonJoining(oldWorldView.reachableNodes)
      val oldIndirectlyConnected = nonJoining(oldWorldView.indirectlyConnectedNodes)
      val oldUnreachable         = nonJoining(oldWorldView.unreachableNodes)

      val updatedReachable           = nonJoining(updatedWorldView.reachableNodes)
      val updatedIndirectlyConnected = nonJoining(updatedWorldView.indirectlyConnectedNodes)
      val updatedUnreachable         = nonJoining(updatedWorldView.unreachableNodes)

      val stable = oldReachable === updatedReachable &&
        // A change between unreachable and indirectly-connected does not affect the stability.
        (oldIndirectlyConnected ++ oldUnreachable) === (updatedIndirectlyConnected ++ updatedUnreachable)

      val increase =
        oldIndirectlyConnected.size < oldIndirectlyConnected.size || oldUnreachable.size < updatedUnreachable.size

      new DiffInfo(stable, increase) {}
    }
  }
}
