package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class StaticQuorumDowner(cluster: Cluster, quorumSize: QuorumSize, stableAfter: FiniteDuration)
    extends Actor
    with ActorLogging {
  import StaticQuorumDowner._

  // TODO is this one ok?
  implicit private val ec: ExecutionContext = context.system.dispatcher

  override def receive: Receive = waitingForSnapshot

  /**
   * Waits for the state snapshot we should get after having
   * subscribed to the cluster's state with the initial
   * state as snapshot.
   */
  private def waitingForSnapshot: Receive = {
    case state: CurrentClusterState => setStabilityTrigger(WorldView(state))
    case _                          => () // ignore // TODO needed?
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
  private def clusterMovement(reachability: WorldView, stabilityTrigger: Cancellable): Receive = {
    case e: MemberEvent =>
      val reachability0 = reachability.memberEvent(e)
      // Only reset trigger if the event impacted the reachability.
      if (reachability0 != reachability) {
        resetStabilityTrigger(reachability0, stabilityTrigger)
      }

    case e: ReachabilityEvent => // TODO check what really should be counted
      val reachability0 = reachability.reachabilityEvent(e)
      // Only reset trigger if the event impacted the reachability.
      if (reachability0 != reachability) {
        resetStabilityTrigger(reachability0, stabilityTrigger)
      }

      resetStabilityTrigger(reachability, stabilityTrigger)
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
  private def handleUnreachableNodes(reachability: WorldView): Unit =
    ReachableNodes(reachability, quorumSize)
      .map { reachableNodeGroup =>
        ResolutionStrategy
          .staticQuorum(reachableNodeGroup, UnreachableNodes(reachability, quorumSize))
          .addressesToDown
          .foreach(cluster.down)
      }
      .fold(err => {
        log.error(s"Oh fuck... $err")
        throw new IllegalStateException(s"Oh fuck... $err")
      }, identity)

  private def scheduleStabilityMessage(): Cancellable =
    context.system.scheduler.scheduleOnce(stableAfter, self, ClusterIsStable)

  override def preStart(): Unit =
    cluster.subscribe(self, InitialStateAsSnapshot, classOf[UnreachableNode], classOf[MemberEvent])

  override def postStop(): Unit =
    cluster.unsubscribe(self)
}

object StaticQuorumDowner {
  def props(cluster: Cluster, quorumSize: QuorumSize, stableAfter: FiniteDuration): Props =
    Props(new StaticQuorumDowner(cluster, quorumSize, stableAfter))

  final case object ClusterIsStable
}
