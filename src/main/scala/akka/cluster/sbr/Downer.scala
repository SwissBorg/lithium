package akka.cluster.sbr

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.sbr.strategies.Or
import akka.cluster.sbr.strategies.downall.DownAll
import akka.cluster.sbr.strategies.indirected.Indirected
import akka.cluster.sbr.strategy.Strategy
import akka.cluster.sbr.strategy.ops._
import akka.cluster.sbr.implicits._
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Downer[A: Strategy](cluster: Cluster,
                          strategy: A,
                          stableAfter: FiniteDuration,
                          downAllWhenUnstable: FiniteDuration)
    extends Actor
    with ActorLogging {

  import Downer._

  // TODO is this one ok?
  implicit private val ec: ExecutionContext = context.system.dispatcher

  // If a node receive a unreachability event in his name it means that it is
  // indirectly connected. It is unreachable via a link but reachable via another as
  // it receive the event.
  // As cluster events are only gossiped to reachable nodes,
  // a node that has been detected as unreachable will never receive an unreachability
  // event in his name.
  private val mediator = DistributedPubSub(cluster.system).mediator
  mediator ! Subscribe("info", self)

  override def receive: Receive = waitingForSnapshot

  /**
   * Waits for the state snapshot we should get after having
   * subscribed to the cluster's state with the initial
   * state as snapshot.
   */
  private def waitingForSnapshot: Receive = {
    case state: CurrentClusterState =>
      val worldView = WorldView(cluster.selfMember, state)

      if (worldView.unreachableNodes.nonEmpty) {
        context.become(
          hasUnreachableNodes(
            worldView,
            scheduleStabilityTrigger(),
            scheduleInstabilityTrigger() // cluster has already an unreachable node
          )
        )
      } else {
        context.become(noUnreachableNodes(worldView, scheduleStabilityTrigger()))
      }

    case _ => () // ignore // TODO needed?
  }

  /**
   * Actor's state when the cluster has no unstable nodes.
   *
   * At this point the unstability message has not been scheduled yet.
   *
   * @param worldView        the current world view
   * @param stabilityTrigger the handle to the stability trigger
   */
  private def noUnreachableNodes(worldView: WorldView, stabilityTrigger: Cancellable): Receive = {
    case ClusterIsStable =>
      println("noUnreachableNodes")
      runStrategy(worldView)
      context.become(noUnreachableNodes(worldView, scheduleStabilityTrigger()))

    case e: MemberEvent =>
      println(s"EVENT0: $e")
      onMemberEvent(worldView, e) { w =>
        context.become(
          noUnreachableNodes(
            w,
            if (w === worldView) stabilityTrigger else resetStabilityTrigger(stabilityTrigger)
          )
        )
      }

    case e: UnreachableMember =>
      mediator ! Publish("info", Wrapper(e))

      println(s"EVENT0: $e")
      onReachabilityEvent(worldView, e) { w =>
        context.become(
          hasUnreachableNodes(
            w,
            if (w === worldView) stabilityTrigger else resetStabilityTrigger(stabilityTrigger),
            scheduleInstabilityTrigger()
          )
        )
      }

    case e: ReachableMember =>
      println(s"EVENT0: $e")
      onReachabilityEvent(worldView, e) { w =>
        context.become(
          noUnreachableNodes(
            w,
            if (w === worldView) stabilityTrigger else resetStabilityTrigger(stabilityTrigger)
          )
        )
      }

    case Wrapper(e: UnreachableMember) =>
      if (e.member === cluster.selfMember) {
        onReachabilityEvent(worldView, e) { w =>
          context.become(
            noUnreachableNodes(
              w,
              if (w === worldView) stabilityTrigger else resetStabilityTrigger(stabilityTrigger)
            )
          )
        }
      }
  }

  /**
   * Actor's state when the cluster contains at least one unstable node.
   *
   * @param worldView          the current world view
   * @param stabilityTrigger   the handle to the stability trigger
   * @param unstabilityTrigger the handle to the stability trigger
   */
  private def hasUnreachableNodes(worldView: WorldView,
                                  stabilityTrigger: Cancellable,
                                  unstabilityTrigger: Cancellable): Receive = {
    case ClusterIsStable =>
      println("hasUnreachableNodes")
      runStrategy(worldView)
      // Does not directly assume all the unreachable nodes have been handled.
      context.become(hasUnreachableNodes(worldView, scheduleStabilityTrigger(), unstabilityTrigger))

    case ClusterIsUnstable =>
      unstabilityTrigger.cancel() // also cancel the related timeout
      downAllNodes(worldView)
      // Not strictly necessary, the cluster should be down at this point.
      context.become(noUnreachableNodes(worldView, resetStabilityTrigger(stabilityTrigger)))

    case e: MemberEvent =>
      println(s"EVENT1: $e")
      onMemberEvent(worldView, e)(updateState(worldView, stabilityTrigger, unstabilityTrigger))

    case e: UnreachableMember =>
      mediator ! Publish("info", Wrapper(e)) // to detect indirectly connected nodes.
      println(s"EVENT1: $e")
      onReachabilityEvent(worldView, e)(updateState(worldView, stabilityTrigger, unstabilityTrigger))

    case e: ReachableMember =>
      println(s"EVENT1: $e")
      onReachabilityEvent(worldView, e)(updateState(worldView, stabilityTrigger, unstabilityTrigger))

    case Wrapper(e: UnreachableMember) =>
      if (e.member === cluster.selfMember) {
        onReachabilityEvent(worldView, e)(updateState(worldView, stabilityTrigger, unstabilityTrigger))
      }
  }

  /**
   * Applies `onUpdatedWorldView` on the new world view generated by the event.
   *
   * @param worldView          the current world view
   * @param e                  the event
   * @param onUpdatedWorldView the function to execute on the new world view
   */
  private def onMemberEvent(worldView: WorldView, e: MemberEvent)(onUpdatedWorldView: WorldView => Unit): Unit =
    worldView.memberEvent(e).map(onUpdatedWorldView).toTry.get

  /**
   * Applies `onUpdatedWorldView` on the new world view generated by the event.
   *
   * @param worldView          the current world view
   * @param e                  the event
   * @param onUpdatedWorldView the function to execute on the new world view
   */
  private def onReachabilityEvent(worldView: WorldView,
                                  e: ReachabilityEvent)(onUpdatedWorldView: WorldView => Unit): Unit =
    worldView.reachabilityEvent(e).map(onUpdatedWorldView).toTry.get

  private def updateState(oldWorldView: WorldView, stabilityTrigger: Cancellable, unstabilityTrigger: Cancellable)(
    newWorldView: WorldView
  ): Unit =
    context.become(
      hasUnreachableNodes(
        newWorldView,
        if (newWorldView === oldWorldView) stabilityTrigger else resetStabilityTrigger(stabilityTrigger),
        if (newWorldView.unreachableNodes.nonEmpty) unstabilityTrigger else resetUnstabilityTrigger(unstabilityTrigger)
      )
    )

  /**
   * Runs [[strategy]] with the strategy to remove indirectly connected nodes.
   */
  private def runStrategy(worldView: WorldView): Unit = {
    println(s"WV: $worldView")
    Or(strategy, Indirected)
      .takeDecision(worldView)
      .leftMap { err =>
        log.error(s"$err")
        err
      }
      .foreach(executeDecision)
  }

  /**
   * Downs all the nodes in the cluster.
   */
  private def downAllNodes(worldView: WorldView): Unit =
    DownAll
      .takeDecision(worldView)
      .leftMap { err =>
        log.error(s"$err")
        err
      }
      .foreach(executeDecision)

  /**
   * Executes the decision.
   *
   * If the current node is the leader all the nodes referred in the decision
   * will be downed. Otherwise, if it is not the leader or none exists, and refers to itself.
   * It will down the current node. Else, no node will be downed.
   *
   * In short, the leader can down anyone. Other nodes are only allowed to down themselves.
   */
  private def executeDecision(decision: StrategyDecision): Unit = {
    println(s"DECISION: $decision")
    if (cluster.state.leader.contains(cluster.selfAddress)) {
      val nodesToDown = decision.nodesToDown
      println(s"Downing nodes: $nodesToDown")
      nodesToDown.foreach(node => cluster.down(node.node.address))
    } else {
      if (decision.nodesToDown.map(_.node).contains(cluster.selfMember)) {
        println(s"Downing self: $cluster.selfMember")
        cluster.down(cluster.selfAddress)
      } else {
        println("Non-leader cannot down other nodes.")
      }
    }
  }

  private def scheduleStabilityTrigger(): Cancellable =
    context.system.scheduler.scheduleOnce(stableAfter, self, ClusterIsStable)

  /**
   * Schedules an instability trigger. In parallel also start a related timeout that will be
   * used to cancel the instability trigger.
   *
   * @return a handle that will cancel both the instability trigger and the related timeout.
   */
  private def scheduleInstabilityTrigger(): Cancellable = {
    val c1 = context.system.scheduler.scheduleOnce(stableAfter * 2, self, ClusterIsUnstableTimeout)
    val c2 = context.system.scheduler.scheduleOnce(stableAfter + downAllWhenUnstable, self, ClusterIsUnstable)
    new Cancellable {
      private val b: AtomicBoolean = new AtomicBoolean(c1.isCancelled && c2.isCancelled)

      override def cancel(): Boolean    = c1.cancel() && c2.cancel()
      override def isCancelled: Boolean = b.getAndSet(c1.isCancelled && c2.isCancelled)
    }
  }

  /**
   * Resets the stability trigger and returns a new handle.
   *
   * @param stabilityTrigger the trigger to reset
   * @return the new handle
   */
  private def resetStabilityTrigger(stabilityTrigger: Cancellable): Cancellable = {
    stabilityTrigger.cancel()
    scheduleStabilityTrigger()
  }

  /**
   * Resets the instability trigger and returns a new handle.
   *
   * @param instabilityTrigger the trigger to reset
   * @return the new handle
   */
  private def resetUnstabilityTrigger(instabilityTrigger: Cancellable): Cancellable = {
    instabilityTrigger.cancel()
    scheduleInstabilityTrigger()
  }

  override def preStart(): Unit = {
    println(s"GLOB: ${cluster.selfAddress.hasGlobalScope}")

    cluster.subscribe(self,
                      InitialStateAsSnapshot,
                      classOf[akka.cluster.ClusterEvent.MemberEvent],
                      classOf[akka.cluster.ClusterEvent.ReachabilityEvent])
  }

  override def postStop(): Unit =
    cluster.unsubscribe(self)
}

object Downer {
  def props[A: Strategy](cluster: Cluster,
                         strategy: A,
                         stableAfter: FiniteDuration,
                         downAllWhenUnstable: FiniteDuration): Props =
    Props(new Downer(cluster, strategy, stableAfter, downAllWhenUnstable))

  final case object ClusterIsStable
  final case object ClusterIsUnstable
  final case object ClusterIsUnstableTimeout

  final case class Wrapper(e: ReachabilityEvent)
}
