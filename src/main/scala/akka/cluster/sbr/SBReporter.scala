package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Stash, Timers}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member}
import cats.data.{State, StateT}
import cats.effect.SyncIO
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
 * Actor reporting on split-brain events.
 *
 * @param splitBrainResolver the actor that resolves the split-brain scenarios.
 * @param stableAfter duration during which a cluster has to be stable before attempting to resolve a split-brain.
 * @param downAllWhenUnstable
 */
class SBReporter(splitBrainResolver: ActorRef, stableAfter: FiniteDuration, downAllWhenUnstable: FiniteDuration)
    extends Actor
    with Stash
    with ActorLogging
    with Timers {
  import SBReporter._

  private val cluster            = Cluster(context.system)
  private val selfMember: Member = cluster.selfMember

  private val _ = context.system.actorOf(SBFailureDetector.props(self), "sbr-fd")

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case s: CurrentClusterState =>
      unstashAll()
      context.become(active(SBReporterState.fromSnapshot(s, selfMember)))

    case _ => stash()
  }

  private def active(state: SBReporterState): Receive = {
    case e: MemberEvent                   => context.become(active(memberEvent(e).runS(state).unsafeRunSync()))
    case SeenChanged(convergence, seenBy) => context.become(active(seenChanged(seenBy).runS(state).value))
    case ReachableMember(m)               => context.become(active(reachableMember(m).runS(state).unsafeRunSync()))
    case UnreachableMember(m)             => context.become(active(unreachableMember(m).runS(state).unsafeRunSync()))
    case IndirectlyConnectedMember(m)     => context.become(active(indirectlyConnected(m).runS(state).unsafeRunSync()))
    case HandleSplitBrain                 => context.become(active(handleSplitBrain.runS(state).unsafeRunSync()))
  }

  private def seenChanged(seenBy: Set[Address]): State[SBReporterState, Unit] = State.modify(_.flush(seenBy))

  private def modify(
    f: SBReporterState => SBReporterState
  ): StateT[SyncIO, SBReporterState, Unit] =
    StateT.modifyF { state =>
      resetHandleSplitBrain.map(_ => f(state))
    }

  private def memberEvent(e: MemberEvent): StateT[SyncIO, SBReporterState, Unit] =
    modify(_.enqueue(e))

  private def reachableMember(m: Member): StateT[SyncIO, SBReporterState, Unit] =
    modify(_.reachableMember(m))

  private def unreachableMember(m: Member): StateT[SyncIO, SBReporterState, Unit] =
    modify(_.unreachableMember(m))

  private def indirectlyConnected(m: Member): StateT[SyncIO, SBReporterState, Unit] =
    modify(_.indirectlyConnected(m))

  private val handleSplitBrain: StateT[SyncIO, SBReporterState, Unit] =
    StateT.inspectF(state => SyncIO(splitBrainResolver ! SBResolver.HandleSplitBrain(state.worldView)))

  private val scheduleHandleSplitBrain: SyncIO[Unit] =
    SyncIO(timers.startSingleTimer(HandleSplitBrain, HandleSplitBrain, stableAfter))

  private val cancelHandleSplitBrain: SyncIO[Unit] = SyncIO(timers.cancel(HandleSplitBrain))

  private val resetHandleSplitBrain: SyncIO[Unit] = cancelHandleSplitBrain >> scheduleHandleSplitBrain

  implicit private val ec: ExecutionContext = context.system.dispatcher

  override def preStart(): Unit = {
    cluster.subscribe(self, InitialStateAsSnapshot, classOf[akka.cluster.ClusterEvent.MemberEvent])
    context.system.eventStream.subscribe(self, classOf[SeenChanged])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    context.system.eventStream.unsubscribe(self)
    timers.cancel(HandleSplitBrain)
  }
}

object SBReporter {
  def props(downer: ActorRef, stableAfter: FiniteDuration, downAllWhenUnstable: FiniteDuration): Props =
    Props(new SBReporter(downer, stableAfter, downAllWhenUnstable))

  /**
   * For internal use.
   */
  final case object HandleSplitBrain
  final case class IndirectlyConnectedMember(member: Member)
}
