package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class StabilityReporter(downer: ActorRef,
                        stableAfter: FiniteDuration,
                        downAllWhenUnstable: FiniteDuration,
                        cluster: Cluster)
    extends Actor
    with ActorLogging {
  import StabilityReporter._

  private val _ = context.system.actorOf(SBRFailureDetector.props(self, cluster), "sbr-fd")

  private var _handleIndirectlyConnected: Option[Cancellable] = None
  private var _handleSplitBrain: Option[Cancellable]          = None
//  private var clusterIsUnstable: Option[Cancellable]       = None

  override def receive: Receive = {
    case state: CurrentClusterState =>
      val worldView = WorldView(cluster.selfMember, trackIndirectlyConnected = true, state)

      startHandleIndirectlyConnected()
      startHandleSplitBrain()
      context.become(main(worldView))

    case _ => () // ignore
  }

  /**
   * The actors main receive.
   *
   * @param worldView the current world view of the cluster from the point of view of current cluster node.
   */
  def main(worldView: WorldView): Receive = {
    def stabilityAndBecome(updatedWorldView: WorldView): Unit = {
      if (!updatedWorldView.isStableChange(worldView)) {
        resetHandleIndirectlyConnected()
        resetHandleSplitBrain()
      }

//      if (updatedWorldView.hasSplitBrain) {
//        // Start the instability timeout when a split-brain
//        // scenario occurs for the first time.
//        startClusterIsUnstable()
//      } else {
//        // Cancel the instability timeout when a split-brain
//        // scenario disappears.
//        cancelClusterIsUnstable()
//      }

      context.become(main(updatedWorldView))
    }

    {
      case e: MemberEvent => stabilityAndBecome(worldView.memberEvent(e))

      case e: ReachabilityEvent =>
        log.debug("{}", e)
        stabilityAndBecome(worldView.reachabilityEvent(e))

      case i @ IndirectlyConnectedNode(member) =>
        log.debug("{}", i)
        stabilityAndBecome(worldView.indirectlyConnected(member))

      case HandleIndirectlyConnected =>
        log.debug("Handle indirectly connected")
        downer ! Downer.HandleIndirectlyConnected(worldView)

      case HandleSplitBrain =>
//        cancelClusterIsUnstable()
        log.debug("Handle split brain")
        downer ! Downer.HandleSplitBrain(worldView)

//      case ClusterIsUnstable =>
//        cancelDownIndirectlyConnected()
//        downer ! Downer.ClusterIsUnstable(worldView)
    }
  }

  /* ------ Indirectly connected ------ */

  private def startHandleIndirectlyConnected(): Unit =
    _handleIndirectlyConnected match {
      case None    => _handleIndirectlyConnected = Some(scheduleHandleIndirectlyConnected())
      case Some(_) => () // already started
    }

  private def resetHandleIndirectlyConnected(): Unit =
    _handleIndirectlyConnected.foreach { c =>
      log.debug("Resetting handleIndirectlyConnected")
      c.cancel()
      _handleIndirectlyConnected = Some(scheduleHandleIndirectlyConnected())
    }

  private def cancelHandleIndirectlyConnected(): Unit =
    _handleIndirectlyConnected.foreach { c =>
      log.debug("Cancelling handleIndirectlyConnected")
      c.cancel()
      _handleIndirectlyConnected = None
    }

  /* ------ Split brain ------ */

  private def startHandleSplitBrain(): Unit =
    _handleSplitBrain match {
      case None    => _handleSplitBrain = Some(scheduleHandleSplitBrain())
      case Some(_) => () // already started
    }

  private def resetHandleSplitBrain(): Unit =
    _handleSplitBrain.foreach { c =>
      log.debug("Resetting handleSplitBrain")
      c.cancel()
      _handleSplitBrain = Some(scheduleHandleSplitBrain())
    }

  private def cancelHandleSplitBrain(): Unit =
    _handleSplitBrain.foreach { c =>
      log.debug("Cancelling handleSplitBrain")
      c.cancel()
      _handleSplitBrain = None
    }

//
//  private def startClusterIsUnstable(): Unit =
//    clusterIsUnstable match {
//      case None =>
//        log.debug("Starting clusterIsUnstable")
//        clusterIsUnstable = Some(scheduleClusterIsUnstable())
//      case Some(_) => () // do nothing
//    }
//
//  private def cancelClusterIsUnstable(): Unit =
//    clusterIsUnstable.foreach { c =>
//      log.debug("Cancelling clusterIsUnstable")
//      c.cancel()
//      clusterIsUnstable = None
//    }

  private def scheduleHandleIndirectlyConnected(): Cancellable =
    context.system.scheduler.scheduleOnce(stableAfter, self, HandleIndirectlyConnected)

  private def scheduleHandleSplitBrain(): Cancellable =
    context.system.scheduler.scheduleOnce(stableAfter * 2, self, HandleSplitBrain)

//  private def scheduleClusterIsUnstable(): Cancellable =
//    context.system.scheduler.scheduleOnce(stableAfter + downAllWhenUnstable, self, ClusterIsUnstable)

  implicit private val ec: ExecutionContext = context.system.dispatcher

  override def preStart(): Unit =
    cluster.subscribe(self, InitialStateAsSnapshot, classOf[akka.cluster.ClusterEvent.MemberEvent])

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    cancelHandleIndirectlyConnected()
    cancelHandleSplitBrain()
//    cancelClusterIsUnstable()
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
  final case object HandleIndirectlyConnected
//  final case object ClusterIsUnstable
}
