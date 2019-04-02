package akka.cluster.sbr

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Address, Cancellable, Props}
import akka.cluster.{Cluster, Member}
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Put, Send, Subscribe}
import akka.cluster.sbr.NoUnreachableNodesState.{OnReachable, OnUnreachable}
import akka.cluster.sbr.UnreachableNodesState.{OnMemberEvent, OnNoUnreachableNodes, OnUnreachableNodes}

import scala.collection.SortedSet
//import akka.cluster.sbr.strategies.Or
import akka.cluster.sbr.strategies.downall.DownAll
//import akka.cluster.sbr.strategies.indirected.Indirected
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
  mediator ! Subscribe(cluster.selfAddress.toString, context.self)

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
            UnreachableNodesState(
              worldView,
              scheduleStabilityTrigger(),
              scheduleInstabilityTrigger(), // cluster has already an unreachable node
              worldView.unreachableNodes.foldLeft(Snitches(mediator ! Publish(_, _))) {
                case (snitches, node) => snitches.snitch(UnreachableMember(node.member))
              }
            )
          )
        )
      } else {
        context.become(noUnreachableNodes(NoUnreachableNodesState(worldView, Snitches(mediator ! Publish(_, _)))))
      }

    case _ => () // ignore // TODO needed?
  }

  /**
   * Actor's state when the cluster has no unstable nodes.
   *
   * At this point the unstability message has not been scheduled yet.
   *
   */
  private def noUnreachableNodes(s: NoUnreachableNodesState): Receive = {
    case ClusterIsStable =>
      println("noUnreachableNodes")
      println(s"BLA: ${cluster.state.unreachable}")
      runStrategy(s.worldView)
//      context.become(noUnreachableNodes(s.copy(stabilityTrigger = scheduleStabilityTrigger()))

    case e: MemberEvent =>
      println(s"EVENT0: $e")
      context.become(s.onMemberEvent(e)(noUnreachableNodes))

    case e: UnreachableMember =>
      println(s"EVENT0: $e")

      context.become(
        s.onReachabilityEvent(e)(
          OnUnreachable(hasUnreachableNodes,
                        scheduleInstabilityTrigger,
                        scheduleInstabilityTrigger,
                        mediator ! Publish(_, _)),
          OnReachable(noUnreachableNodes)
        )
      )
  }

  /**
   * Actor's state when the cluster contains at least one unstable node.
   *
   */
  private def hasUnreachableNodes(s: UnreachableNodesState): Receive = {
    case ClusterIsStable =>
      println("hasUnreachableNodes")
      println(s"BLA: ${cluster.state.unreachable}")
      runStrategy(s.worldView)
      // Does not directly assume all the unreachable nodes have been handled.
      context.become(hasUnreachableNodes(s.copy(stabilityTrigger = scheduleStabilityTrigger())))

    case ClusterIsUnstable =>
      s.instabilityTrigger.cancel() // also cancel the related timeout
      downAllNodes(s.worldView)
      // Not strictly necessary, the cluster should be down at this point.
      context.become(
        noUnreachableNodes(
          s.copy(stabilityTrigger = resetStabilityTrigger(s.stabilityTrigger), snitches = s.snitches.cancelAll())
        )
      )

    case e: MemberEvent =>
      println(s"EVENT1: $e")
      context.become(s.onMemberEvent(e)(OnMemberEvent(hasUnreachableNodes, resetStabilityTrigger)))

    case e: ReachabilityEvent =>
      println(s"EVENT1: $e")
      context.become(
        s.onReachabilityEvent(e)(
          OnNoUnreachableNodes(noUnreachableNodes, _.cancel()),
          OnUnreachableNodes(hasUnreachableNodes, resetStabilityTrigger)
        )
      )
  }

  /**
   * Runs [[strategy]] with the strategy to remove indirectly connected nodes.
   */
  private def runStrategy(worldView: WorldView): Unit = {
    println(s"WV: $worldView")
//    Or(strategy, Indirected)
    strategy
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
  private def executeDecision(decision: StrategyDecision): Unit =
    println(s"DECISION: $decision")
//    if (cluster.state.leader.contains(cluster.selfAddress)) {
//      val nodesToDown = decision.nodesToDown
//      println(s"Downing nodes: $nodesToDown")
//      nodesToDown.foreach(node => cluster.down(node.member.address))
//    } else {
//      if (decision.nodesToDown.map(_.member).contains(cluster.selfMember)) {
//        println(s"Downing self: $cluster.selfMember")
//        cluster.down(cluster.selfAddress)
//      } else {
//        println("Non-leader cannot down other nodes.")
//      }
//    }

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
  private def resetInstabilityTrigger(instabilityTrigger: Cancellable): Cancellable = {
    instabilityTrigger.cancel()
    scheduleInstabilityTrigger()
  }

  override def preStart(): Unit =
    cluster.subscribe(self,
                      InitialStateAsSnapshot,
                      classOf[akka.cluster.ClusterEvent.MemberEvent],
                      classOf[akka.cluster.ClusterEvent.ReachabilityEvent])

  override def postStop(): Unit = cluster.unsubscribe(self)
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

  sealed abstract class Snitch {
    val ackN: Long
  }

  final case class SecondGuessUnreachableRequest(member: Member, ackN: Long) extends Snitch {
    def respond: SecondGuessResponse = SecondGuessResponse(member, ackN)
  }

  final case class SecondGuessReachableRequest(member: Member, ackN: Long) extends Snitch {
    def respond: SecondGuessResponse = SecondGuessResponse(member, ackN)
  }

  final case class SecondGuessResponse(member: Member, ackN: Long) extends Snitch
}
