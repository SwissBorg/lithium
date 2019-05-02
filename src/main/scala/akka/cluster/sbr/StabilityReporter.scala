package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Stash}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class StabilityReporter(downer: ActorRef,
                        stableAfter: FiniteDuration,
                        downAllWhenUnstable: FiniteDuration,
                        cluster: Cluster)
    extends Actor
    with Stash
    with ActorLogging {
  import StabilityReporter._

  private val selfMember: Member = cluster.selfMember

  private val _ = context.system.actorOf(SBRFailureDetector.props(self), "sbr-fd")

  private var _handleSplitBrain: Cancellable = scheduleHandleSplitBrain()

  private var _state: StabilityReporterState = StabilityReporterState(selfMember)

  override def receive: Receive = initializing

  private def initializing: Receive = {
    case s: CurrentClusterState =>
      unstashAll()
      _state = StabilityReporterState.fromSnapshot(s, selfMember)
      context.become(active)

    case _ => stash()
  }

  private def active: Receive = {
    case e: MemberEvent =>
      resetHandleSplitBrain()
      _state = _state.enqueue(e)

    case SeenChanged(convergence, seenBy) =>
      _state = _state.flush(seenBy)

    case e: ReachabilityEvent =>
      log.debug("{}", e)
      resetHandleSplitBrain()

      e match {
        case UnreachableMember(member) => _state = _state.unreachableMember(member)
        case ReachableMember(member)   => _state = _state.reachableMember(member)
      }

    case i @ IndirectlyConnectedMember(member) =>
      log.debug("{}", i)
      resetHandleSplitBrain()
      _state = _state.indirectlyConnected(member)

    case HandleSplitBrain =>
      //        cancelClusterIsUnstable()
      log.debug("Handle split brain")
      downer ! Downer.HandleSplitBrain(_state.worldView)
  }

  private def resetHandleSplitBrain(): Unit = {
    _handleSplitBrain.cancel()
    _handleSplitBrain = scheduleHandleSplitBrain()
  }

  private def scheduleHandleSplitBrain(): Cancellable =
    context.system.scheduler.scheduleOnce(stableAfter, self, HandleSplitBrain)

  implicit private val ec: ExecutionContext = context.system.dispatcher

  override def preStart(): Unit = {
    cluster.subscribe(self, InitialStateAsSnapshot, classOf[akka.cluster.ClusterEvent.MemberEvent])
    context.system.eventStream.subscribe(self, classOf[SeenChanged])

  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    context.system.eventStream.unsubscribe(self)
    _handleSplitBrain.cancel()
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
