package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus.{Down, Removed}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class StabilityReporter(downer: ActorRef,
                        stableAfter: FiniteDuration,
                        downAllWhenUnstable: FiniteDuration,
                        cluster: Cluster)
    extends Actor
    with ActorLogging {
  import StabilityReporter._

  private val reporter = context.system.actorOf(IndirectConnectionReporter.props(self), "notifier")

  private var clusterIsStable: Option[Cancellable]   = None
  private var clusterIsUnstable: Option[Cancellable] = None

  override def receive: Receive = {
    case state: CurrentClusterState =>
      val worldView = WorldView(cluster.selfMember, state)
      worldView.unreachableNodes.foreach(node => notifyIfReachable(UnreachableMember(node.member)))
      startClusterIsStable()
      context.become(main(worldView))

    case _ => () // ignore
  }

  def main(worldView: WorldView): Receive = {
    def updated(updatedWorldView: WorldView): Unit = {
      if (!updatedWorldView.isStableChange(worldView)) {
        resetClusterIsStable()
      }

      if (updatedWorldView.hasSplitBrain) {
        // Start the instability timeout when a split-brain
        // scenario occurs for the first time.
        startClusterIsUnstable()
      } else {
        // Cancel the instability timeout when a split-brain
        // scenario disappears.
        cancelClusterIsUnstable()
      }

      context.become(main(updatedWorldView))
    }

    {
      case e: MemberEvent =>
        updated(worldView.memberEvent(e))

      case e: ReachabilityEvent =>
        notifyIfReachable(e)
        updated(worldView.reachabilityEvent(e))

      case IndirectConnectionReporter.Notification(e: ReachabilityEvent, id) =>
        reporter ! IndirectConnectionReporter.Ack(id, cluster.selfMember.address)
        updated(worldView.reachabilityEvent(e))

      case ClusterIsStable =>
        cancelClusterIsUnstable()
        downer ! Downer.ClusterIsStable(worldView)

      case ClusterIsUnstable =>
        cancelClusterIsStable()
        downer ! Downer.ClusterIsUnstable(worldView)
    }
  }

  // todo need to send reachable?
  private def notifyIfReachable(e: ReachabilityEvent): Unit =
    if (!cluster.failureDetector.isAvailable(e.member.address) || e.member.status == Down || e.member.status == Removed)
      log.debug(s"[notify-if-reachable] Not notifying {} of {}", e.member.address, e)
    else {
      // only notify available and non-exiting members.
      log.debug(s"[notify-if-reachable] Notifying {} of {}", e.member.address, e)
      reporter ! IndirectConnectionReporter.Report(e, e.member.address)
    }

  private def startClusterIsStable(): Unit =
    clusterIsStable match {
      case None    => clusterIsStable = Some(scheduleClusterIsStable())
      case Some(_) => () // already started
    }

  private def resetClusterIsStable(): Unit =
    clusterIsStable.foreach { c =>
      log.debug("Resetting clusterIsStable")
      c.cancel()
      clusterIsStable = Some(scheduleClusterIsStable())
    }

  private def cancelClusterIsStable(): Unit =
    clusterIsUnstable.foreach { c =>
      log.debug("Cancelling clusterIsStable")
      c.cancel()
      clusterIsUnstable = None
    }

  private def startClusterIsUnstable(): Unit =
    clusterIsUnstable match {
      case None =>
        log.debug("Starting clusterIsUnstable")
        clusterIsUnstable = Some(scheduleClusterIsUnstable())
      case Some(_) => () // do nothing
    }

  private def cancelClusterIsUnstable(): Unit =
    clusterIsUnstable.foreach { c =>
      log.debug("Cancelling clusterIsUnstable")
      c.cancel()
      clusterIsUnstable = None
    }

  private def scheduleClusterIsStable(): Cancellable =
    context.system.scheduler.scheduleOnce(stableAfter, self, ClusterIsStable)

  private def scheduleClusterIsUnstable(): Cancellable =
    context.system.scheduler.scheduleOnce(stableAfter + downAllWhenUnstable, self, ClusterIsUnstable)

  implicit private val ec: ExecutionContext = context.system.dispatcher

  override def preStart(): Unit =
    cluster.subscribe(self,
                      InitialStateAsSnapshot,
                      classOf[akka.cluster.ClusterEvent.MemberEvent],
                      classOf[akka.cluster.ClusterEvent.ReachabilityEvent])

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    cancelClusterIsStable()
    cancelClusterIsUnstable()
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
  final case object ClusterIsStable
  final case object ClusterIsUnstable
}
