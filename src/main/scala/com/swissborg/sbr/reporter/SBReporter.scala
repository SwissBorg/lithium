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
import com.swissborg.sbr.resolver.SBResolver
import com.swissborg.sbr.{Converter, SBSeenChanged, WorldView}
import com.swissborg.sbr.implicits._

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
      modifiyAndAssess(_.consumeQueue(seenBy).pruneRemoved)
    } else {
      modifiyAndAssess(_.consumeQueue(seenBy))
    }

  /**
   * Modify the state using `f` and TODO
   */
  private def modifiyAndAssess(f: SBReporterState => SBReporterState): Eval[Unit] =
    assessStableChange(f) >> assessSplitBrain

  private def enqueue(e: MemberEvent): Eval[Unit] =
    modifiyAndAssess(_.enqueue(e))

  private def withReachableMember(m: Member): Eval[Unit] =
    modifiyAndAssess(_.withReachableMember(m))

  private def withUnreachableMember(m: Member): Eval[Unit] =
    modifiyAndAssess(_.withUnreachableMember(m))

  private def withIndirectlyConnectedMember(m: Member): Eval[Unit] =
    modifiyAndAssess(_.withIndirectlyConnectedMember(m))

  private val scheduleClusterIsStable: SyncIO[Unit] =
    SyncIO(timers.startSingleTimer(ClusterIsStable, ClusterIsStable, stableAfter))

  private val cancelClusterIsStable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsStable))

  private val resetClusterIsStable: SyncIO[Unit] = cancelClusterIsStable >> scheduleClusterIsStable

  private val scheduleClusterIsUnstable: SyncIO[Unit] =
    SyncIO(
      timers
        .startSingleTimer(ClusterIsUnstable, ClusterIsUnstable, stableAfter + ((stableAfter.toMillis * 0.75) millis))
    )

  private val cancelClusterIsUnstable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsUnstable))

  private val resetClusterIsUnstable: SyncIO[Unit] = cancelClusterIsUnstable >> scheduleClusterIsUnstable

  private val clusterIsUnstableIsActive: SyncIO[Boolean] = SyncIO(timers.isTimerActive(ClusterIsUnstable))

  /**
   * Send the resolver the order to run a split-brain resolution.
   *
   * If there's not split-brain, does nothing.
   */
  private val handleSplitBrain: Eval[Unit] =
    for {
      _ <- liftF[SyncIO, SBReporterState, Unit](cancelClusterIsStable)
      _ <- ifSplitBrain(SBResolver.HandleSplitBrain(_))
    } yield ()

  private val downAll: Eval[Unit] = ifSplitBrain(SBResolver.DownAll)

  private def ifSplitBrain(event: WorldView => SBResolver.Event): Eval[Unit] =
    inspectF { state =>
      if (hasSplitBrain(state.worldView)) {
        SyncIO(splitBrainResolver ! event(state.worldView))
      } else {
        SyncIO.unit
      }
    }

  private val assessSplitBrainStarted: Eval[Unit] = liftF(
    clusterIsUnstableIsActive.ifM(SyncIO.unit, scheduleClusterIsUnstable)
  )

  private val assessSplitBrainResolved: Eval[Unit] = inspectF { state =>
    if (hasSplitBrain(state.worldView)) SyncIO.unit
    else resetClusterIsUnstable
  }

  private val assessSplitBrain: Eval[Unit] = assessSplitBrainStarted >> assessSplitBrainResolved

  private def assessStableChange(f: SBReporterState => SBReporterState): Eval[Unit] = modifyF { state =>
    val updatedState = f(state)

    val run = if (isStableChange(state.worldView, updatedState.worldView)) {
      SyncIO.unit
    } else {
      resetClusterIsStable
    }

    run.as(updatedState)
  }

  private def hasSplitBrain(worldView: WorldView): Boolean =
    worldView.unreachableNodes.nonEmpty || worldView.indirectlyConnectedNodes.nonEmpty

  private def isStableChange(worldView0: WorldView, worldView1: WorldView): Boolean = {
    val members0 = worldView0.members.filterNot(member => member.status === Joining || member.status === WeaklyUp)
    val members1 = worldView1.members.filterNot(member => member.status === Joining || member.status === WeaklyUp)

    members0.size === members1.size && members0 === members1
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

  /**
   * For internal use.
   */
  final case object ClusterIsStable
  final case object ClusterIsUnstable
  final case class IndirectlyConnectedMember(member: Member)
}
