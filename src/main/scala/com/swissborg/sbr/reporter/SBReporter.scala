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
class SBReporter(splitBrainResolver: ActorRef, stableAfter: FiniteDuration, downAllWhenUnstable: Boolean)
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
    val diff              = worldViewDiff(state.worldView, updatedState.worldView)

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

    val resetClusterIsStableWhenUnstable = if (diff.changeIsStable) {
      SyncIO.unit
    } else {
      resetClusterIsStable
    }

    for {
      _ <- clusterIsUnstableIsActive.ifM(cancelClusterIsUnstableWhenSplitBrainResolved,
                                         scheduleClusterIsUnstableWhenSplitBrainWorsened)
      _ <- resetClusterIsStableWhenUnstable
    } yield updatedState
  }

  private def enqueue(e: MemberEvent): Eval[Unit] =
    modifyS(_.enqueue(e))

  private def withReachableMember(m: Member): Eval[Unit] =
    modifyS(_.withReachableMember(m))

  private def withUnreachableMember(m: Member): Eval[Unit] =
    modifyS(_.withUnreachableMember(m)) >> liftF(SyncIO(log.debug("withUnreachableMember({})", m)))

  private def withIndirectlyConnectedMember(m: Member): Eval[Unit] =
    modifyS(_.withIndirectlyConnectedMember(m))

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

  private def worldViewDiff(worldView0: WorldView, worldView1: WorldView): WorldViewDiff = {
    val simple0 = worldView0.simple
    val nonJoiningNodes0 = simple0.copy(
      reachableMembers =
        simple0.reachableMembers.filterNot(member => member.status === Joining || member.status === WeaklyUp),
      unreachableMembers = simple0.unreachableMembers
        .filterNot(member => member.status === Joining || member.status === WeaklyUp),
      indirectlyConnectedMembers = simple0.indirectlyConnectedMembers.filterNot(
        member => member.status === Joining || member.status === WeaklyUp
      )
    )

    val simple1 = worldView1.simple
    val nonJoiningNodes1 = simple1.copy(
      reachableMembers =
        simple1.reachableMembers.filterNot(member => member.status === Joining || member.status === WeaklyUp),
      unreachableMembers = simple1.unreachableMembers
        .filterNot(member => member.status === Joining || member.status === WeaklyUp),
      indirectlyConnectedMembers = simple1.indirectlyConnectedMembers.filterNot(
        member => member.status === Joining || member.status === WeaklyUp
      )
    )

    val stable = nonJoiningNodes0 === nonJoiningNodes1
    val increase = !stable &&
      (nonJoiningNodes0.unreachableMembers.size < nonJoiningNodes1.unreachableMembers.size ||
        nonJoiningNodes0.indirectlyConnectedMembers.size < nonJoiningNodes1.indirectlyConnectedMembers.size)

    WorldViewDiff(stable, increase)
  }

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
  type Eval[A] = StateT[SyncIO, SBReporterState, A]

  def props(downer: ActorRef, stableAfter: FiniteDuration, downAllWhenUnstable: Boolean): Props =
    Props(new SBReporter(downer, stableAfter, downAllWhenUnstable))

  final private case class WorldViewDiff(changeIsStable: Boolean, hasNewUnreachableOrIndirectlyConnected: Boolean)

  /**
   * For internal use.
   */
  final case object ClusterIsStable
  final case object ClusterIsUnstable
  final case class IndirectlyConnectedMember(member: Member)
}
