package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Stash, Timers}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member}
import cats.data.{State, StateT}
import cats.data.StateT._
import cats.effect.SyncIO
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

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

  private val _ = context.system.actorOf(SBFailureDetector.props(self), "sbr-fd")

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case s: CurrentClusterState =>
      unstashAll()
      context.become(active(SBReporterState.fromSnapshot(s, selfMember)))

    case _ => stash()
  }

  private def active(state: SBReporterState): Receive = {
    case e: MemberEvent =>
      context.become(active(enqueue(e).runS(state).unsafeRunSync()))

    case SeenChanged(convergence, seenBy) =>
      context.become(active(consumeQueue(convergence, seenBy).runS(state).value))

    case ReachableMember(m) =>
      context.become(active(withReachableMember(m).runS(state).unsafeRunSync()))

    case UnreachableMember(m) =>
      context.become(active(withUnreachableMember(m).runS(state).unsafeRunSync()))

    case IndirectlyConnectedMember(m) =>
      context.become(active(withIndirectlyConnectedMember(m).runS(state).unsafeRunSync()))

    case ClusterIsStable =>
      context.become(active(handleSplitBrain.runS(state).unsafeRunSync()))
  }

  /**
   * Update the changes described by the change queue and prune
   * the removed members if the membership converged.
   */
  private def consumeQueue(convergence: Boolean, seenBy: Set[Address]): State[SBReporterState, Unit] =
    for {
      _ <- State.modify[SBReporterState](_.consumeQueue(seenBy))
      _ <- if (convergence) State.modify[SBReporterState](_.pruneRemoved) else State.pure[SBReporterState, Unit](())
    } yield ()

  /**
   * Modify the state using `f` and reset the cluster-is-stable timer.
   */
  private def resetClusterIsStableAndModify(f: SBReporterState => SBReporterState): Eval[Unit] =
    modifyF { state =>
      resetClusterIsStable.map(_ => f(state))
    }

  private def enqueue(e: MemberEvent): Eval[Unit] = e match {
    case _: MemberJoined | _: MemberWeaklyUp => modify(_.enqueue(e))
    case _                                   => resetClusterIsStableAndModify(_.enqueue(e))
  }

  private def withReachableMember(m: Member): Eval[Unit] =
    resetClusterIsStableAndModify(_.withReachableMember(m))

  private def withUnreachableMember(m: Member): Eval[Unit] =
    resetClusterIsStableAndModify(_.withUnreachableMember(m))

  private def withIndirectlyConnectedMember(m: Member): Eval[Unit] =
    resetClusterIsStableAndModify(_.withIndirectlyConnectedMember(m))

  /**
   * Send the resolver the order to run a split-brain resolution.
   *
   * If there's not split-brain, does nothing.
   */
  private val handleSplitBrain: Eval[Unit] =
    inspectF { state =>
      if (state.worldView.unreachableNodes.nonEmpty || state.worldView.indirectlyConnectedNodes.nonEmpty) {
        SyncIO(splitBrainResolver ! SBResolver.HandleSplitBrain(state.worldView))
      } else {
        SyncIO.unit
      }
    }

  private val scheduleClusterIsStable: SyncIO[Unit] =
    SyncIO(timers.startSingleTimer(ClusterIsStable, ClusterIsStable, stableAfter))

  private val cancelClusterIsStable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsStable))

  private val resetClusterIsStable: SyncIO[Unit] = cancelClusterIsStable >> scheduleClusterIsStable

  override def preStart(): Unit = {
    cluster.subscribe(self, InitialStateAsSnapshot, classOf[akka.cluster.ClusterEvent.MemberEvent])
    context.system.eventStream.subscribe(self, classOf[SeenChanged])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    context.system.eventStream.unsubscribe(self)
    timers.cancel(ClusterIsStable)
  }
}

object SBReporter {
  type Eval[A] = StateT[SyncIO, SBReporterState, A]

  def props(downer: ActorRef, stableAfter: FiniteDuration): Props = Props(new SBReporter(downer, stableAfter))

  /**
   * For internal use.
   */
  final case object ClusterIsStable
  final case class IndirectlyConnectedMember(member: Member)
}
