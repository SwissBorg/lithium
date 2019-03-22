package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import Strategy.StrategyOps

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class Downer[A: Strategy](cluster: Cluster, strategy: A, stableAfter: FiniteDuration) extends Actor with ActorLogging {
  import Downer._

  // TODO is this one ok?
  implicit private val ec: ExecutionContext = context.system.dispatcher

  override def receive: Receive = waitingForSnapshot

  /**
   * Waits for the state snapshot we should get after having
   * subscribed to the cluster's state with the initial
   * state as snapshot.
   */
  private def waitingForSnapshot: Receive = {
    case state: CurrentClusterState =>
      setStabilityTrigger(WorldView(cluster.selfMember, state))
    case _ => () // ignore // TODO needed?
  }

  def mainReceive(reachability: WorldView, stabilityTrigger: Cancellable): Receive =
    clusterStable(reachability, stabilityTrigger).orElse(clusterMovement(reachability, stabilityTrigger))

  /**
   * Listens to a stable cluster signal, detects a split-brain and attempts to resolve it.
   */
  private def clusterStable(reachability: WorldView, stabilityTrigger: Cancellable): Receive = {
    case ClusterIsStable =>
      handleUnreachableNodes(reachability)
      resetStabilityTrigger(reachability, stabilityTrigger)
  }

  /**
   * Listens to cluster movements and resets the stability trigger when necessary.
   */
  private def clusterMovement(worldView: WorldView, stabilityTrigger: Cancellable): Receive = {
    case e: MemberEvent =>
      val maybeWorldView = worldView.memberEvent(e)

      // Only reset trigger if the event impacted the reachability.
      maybeWorldView
        .map { w =>
          if (w != worldView) resetStabilityTrigger(w, stabilityTrigger)
        }
        .toTry
        .get

    case e: ReachabilityEvent =>
      val maybeWorldView = worldView.reachabilityEvent(e)

      maybeWorldView
        .map { w =>
          if (w != worldView) resetStabilityTrigger(w, stabilityTrigger)
        }
        .toTry
        .get
  }

  private def setStabilityTrigger(reachability: WorldView): Unit =
    context.become(mainReceive(reachability, scheduleStabilityMessage()))

  private def resetStabilityTrigger(reachability: WorldView, stabilityTrigger: Cancellable): Unit = {
    stabilityTrigger.cancel()
    setStabilityTrigger(reachability)
  }

  /**
   * Attemps to resolve a split-brain issue if there is one using
   * the static-quorum strategy.
   */
  private def handleUnreachableNodes(worldView: WorldView): Unit = {
    val a = strategy.handle(worldView)

    println(s"DECISION $a")

    a.fold(err => {
        log.error(s"$err")
        throw err
      }, identity)
      .addressesToDown
      .foreach(Cluster(context.system).down)
  }

  private def scheduleStabilityMessage(): Cancellable =
    context.system.scheduler.scheduleOnce(stableAfter, self, ClusterIsStable)

  override def preStart(): Unit =
    cluster.subscribe(self,
                      InitialStateAsSnapshot,
                      classOf[akka.cluster.ClusterEvent.MemberEvent],
                      classOf[akka.cluster.ClusterEvent.ReachabilityEvent])

  override def postStop(): Unit =
    cluster.unsubscribe(self)
}

object Downer {
  def props[A: Strategy](cluster: Cluster, strategy: A, stableAfter: FiniteDuration): Props =
    Props(new Downer(cluster, strategy, stableAfter))

  final case object ClusterIsStable
}
