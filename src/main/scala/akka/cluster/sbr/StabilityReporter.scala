package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Stash, Timers}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member}
import cats.data.{State, StateT}
import cats.effect.SyncIO
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class StabilityReporter(downer: ActorRef,
                        stableAfter: FiniteDuration,
                        downAllWhenUnstable: FiniteDuration,
                        cluster: Cluster)
    extends Actor
    with Stash
    with ActorLogging
    with Timers {
  import StabilityReporter._

  private val selfMember: Member = cluster.selfMember

  private val _ = context.system.actorOf(SBRFailureDetector.props(self), "sbr-fd")

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case s: CurrentClusterState =>
      unstashAll()
      context.become(active(StabilityReporterState.fromSnapshot(s, selfMember)))

    case _ => stash()
  }

  private def active(state: StabilityReporterState): Receive = {
    case e: MemberEvent                   => context.become(active(memberEvent(e).runS(state).unsafeRunSync()))
    case SeenChanged(convergence, seenBy) => context.become(active(seenChanged(seenBy).runS(state).value))
    case ReachableMember(m)               => context.become(active(reachableMember(m).runS(state).unsafeRunSync()))
    case UnreachableMember(m)             => context.become(active(unreachableMember(m).runS(state).unsafeRunSync()))
    case IndirectlyConnectedMember(m)     => context.become(active(indirectlyConnected(m).runS(state).unsafeRunSync()))
    case HandleSplitBrain =>
      log.debug("HERE")
      context.become(active(handleSplitBrain.runS(state).unsafeRunSync()))
  }

  private def seenChanged(seenBy: Set[Address]): State[StabilityReporterState, Unit] = State.modify(_.flush(seenBy))

  private def modify(
    f: StabilityReporterState => StabilityReporterState
  ): StateT[SyncIO, StabilityReporterState, Unit] =
    StateT.modifyF { state =>
      resetHandleSplitBrain.map(_ => f(state))
    }

  private def memberEvent(e: MemberEvent): StateT[SyncIO, StabilityReporterState, Unit] =
    modify(_.enqueue(e))

  private def reachableMember(m: Member): StateT[SyncIO, StabilityReporterState, Unit] =
    modify(_.reachableMember(m))

  private def unreachableMember(m: Member): StateT[SyncIO, StabilityReporterState, Unit] =
    modify(_.unreachableMember(m))

  private def indirectlyConnected(m: Member): StateT[SyncIO, StabilityReporterState, Unit] =
    modify(_.indirectlyConnected(m))

  private val handleSplitBrain: StateT[SyncIO, StabilityReporterState, Unit] =
    StateT.inspectF(state => SyncIO(downer ! Downer.HandleSplitBrain(state.worldView)))

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

object StabilityReporter {
  def props(downer: ActorRef,
            stableAfter: FiniteDuration,
            downAllWhenUnstable: FiniteDuration,
            cluster: Cluster): Props =
    Props(new StabilityReporter(downer, stableAfter, downAllWhenUnstable, cluster))

  /**
   * For internal use.
   */
  final case object HandleSplitBrain
  final case class IndirectlyConnectedMember(member: Member)
}
