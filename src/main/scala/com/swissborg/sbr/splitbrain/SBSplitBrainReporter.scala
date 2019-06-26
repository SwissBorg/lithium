package com.swissborg.sbr.splitbrain

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster._
import cats.data.StateT
import cats.data.StateT._
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.reachability._
import com.swissborg.sbr.resolver._

import scala.collection.immutable.SortedSet
import scala.concurrent.duration._

/**
  * Actor reporting on split-brain events.
  *
  * @param splitBrainResolver the actor that resolves the split-brain scenarios.
  * @param stableAfter duration during which a cluster has to be stable before attempting to resolve a split-brain.
  */
private[sbr] class SBSplitBrainReporter(
    private val splitBrainResolver: ActorRef,
    private val stableAfter: FiniteDuration,
    private val downAllWhenUnstable: Option[FiniteDuration]
) extends Actor
    with Stash
    with ActorLogging
    with Timers {
  import SBSplitBrainReporter._

  private val cluster = Cluster(context.system)
  private val selfMember = cluster.selfMember

  discard(context.actorOf(SBReachabilityReporter.props(self), "sb-reachability-reporter"))

  override def receive: Receive = initializing

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def initializing: Receive = {
    case snapshot: CurrentClusterState =>
      unstashAll()
      context.become(active(SBSplitBrainReporterState.fromSnapshot(selfMember, snapshot)))

    case _ => stash()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def active(state: SBSplitBrainReporterState): Receive = {
    case e: MemberEvent =>
      context.become(active(updateMember(e).runS(state).unsafeRunSync()))

    case e @ NodeReachable(node) =>
      log.debug("{}", e)
      context.become(active(withReachableNode(node).runS(state).unsafeRunSync()))

    case e @ NodeUnreachable(node) =>
      log.debug("{}", e)
      context.become(active(withUnreachableNode(node).runS(state).unsafeRunSync()))

    case e @ NodeIndirectlyConnected(node) =>
      log.debug("{}", e)
      context.become(active(withIndirectlyConnectedNode(node).runS(state).unsafeRunSync()))

    case ClusterIsStable =>
      context.become(active(handleSplitBrain.runS(state).unsafeRunSync()))

    case ClusterIsUnstable =>
      context.become(active(downAll.runS(state).unsafeRunSync()))
  }

  /**
    * Modify the state using `f` and TODO
    */
  private def modifyS(f: SBSplitBrainReporterState => SBSplitBrainReporterState): Res[Unit] =
    modifyF { state =>
      val updatedState = f(state)

      val diff = DiffInfo(state.worldView, updatedState.worldView)

      def cancelClusterIsUnstableIfSplitBrainResolved: SyncIO[Unit] =
        if (hasSplitBrain(state.worldView)) SyncIO.unit
        else cancelClusterIsUnstable

      def scheduleClusterIsUnstableIfSplitBrainWorsened: SyncIO[Unit] =
        if (diff.hasNewUnreachableOrIndirectlyConnected) scheduleClusterIsUnstable
        else SyncIO.unit

      val resetClusterIsStableIfUnstable: SyncIO[Unit] =
        if (diff.changeIsStable) SyncIO.unit
        else resetClusterIsStable

      for {
        _ <- SyncIO(log.debug("DIFF {}", diff))
        _ <- if (downAllWhenUnstable.isDefined)
          clusterIsUnstableIsActive.ifM(
            // When the timer is running it should not be interfered
            // with as it is started when the first non-reachable node
            // is detected. It is stopped when the split-brain has resolved.
            // In this case it healed itself as the `clusterIsUnstable` timer
            // is stopped before a resolution is requested.
            cancelClusterIsUnstableIfSplitBrainResolved,
            // When the timer is not running it means that all the nodes
            // were reachable up to this point or that a resolution has
            // been requested. It is started when new non-reachable nodes
            // appear. That could the 1st non-reachable node or an additional
            // one after a resolution has been requested.
            scheduleClusterIsUnstableIfSplitBrainWorsened
          )
        else
          SyncIO.unit // not downing the partition if it is unstable for too long

        _ <- resetClusterIsStableIfUnstable
      } yield updatedState
    }

  private def updateMember(e: MemberEvent): Res[Unit] =
    modifyS(_.updatedMember(e.member)) >> liftF(SyncIO(log.debug("Member event: {}", e)))

  private def withReachableNode(node: UniqueAddress): Res[Unit] =
    modifyS(_.withReachableNode(node)) >> liftF(
      SyncIO(log.debug("Node became reachable: {}", node))
    )

  private def withUnreachableNode(node: UniqueAddress): Res[Unit] =
    modifyS(_.withUnreachableNode(node)) >> liftF(
      SyncIO(log.debug("Node became unreachable: {}", node))
    )

  private def withIndirectlyConnectedNode(node: UniqueAddress): Res[Unit] =
    modifyS(_.withIndirectlyConnectedNode(node)) >> liftF(
      SyncIO(log.debug("Node became indirectly-connected: {}", node))
    )

  private val scheduleClusterIsStable: SyncIO[Unit] =
    SyncIO(timers.startSingleTimer(ClusterIsStable, ClusterIsStable, stableAfter))

  private val cancelClusterIsStable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsStable))

  private val resetClusterIsStable: SyncIO[Unit] = cancelClusterIsStable >> scheduleClusterIsStable

  private val scheduleClusterIsUnstable: SyncIO[Unit] =
    downAllWhenUnstable.traverse_(
      d => SyncIO(timers.startSingleTimer(ClusterIsUnstable, ClusterIsUnstable, d))
    )

  private val cancelClusterIsUnstable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsUnstable))

  private val clusterIsUnstableIsActive: SyncIO[Boolean] = SyncIO(
    timers.isTimerActive(ClusterIsUnstable)
  )

  /**
    * Send the resolver the order to run a split-brain resolution.
    *
    * If there's not split-brain, does nothing.
    */
  private val handleSplitBrain: Res[Unit] =
    for {
      // Cancel else the partition will be downed if it takes too long for
      // the split-brain to be resolved after `SBResolver` downs the nodes.
      _ <- liftF[SyncIO, SBSplitBrainReporterState, Unit](cancelClusterIsUnstable)
      _ <- ifSplitBrain(SBResolver.HandleSplitBrain(_))
      _ <- liftF(scheduleClusterIsStable)
    } yield ()

  private val downAll: Res[Unit] = for {
    _ <- liftF[SyncIO, SBSplitBrainReporterState, Unit](cancelClusterIsStable)
    _ <- ifSplitBrain(SBResolver.DownAll)
    _ <- liftF(scheduleClusterIsStable)
  } yield ()

  private def ifSplitBrain(event: WorldView => SBResolver.Event): Res[Unit] =
    inspectF { state =>
      if (hasSplitBrain(state.worldView)) {
        SyncIO(splitBrainResolver ! event(state.worldView))
      } else {
        SyncIO.unit
      }
    }

  private def hasSplitBrain(worldView: WorldView): Boolean =
    worldView.unreachableNodes.nonEmpty || worldView.indirectlyConnectedNodes.nonEmpty

  override def preStart(): Unit = {
    cluster.subscribe(self, InitialStateAsSnapshot, classOf[akka.cluster.ClusterEvent.MemberEvent])
    scheduleClusterIsStable.unsafeRunSync()
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    timers.cancel(ClusterIsStable)
    timers.cancel(ClusterIsUnstable)
  }
}

private[sbr] object SBSplitBrainReporter {
  private type Res[A] = StateT[SyncIO, SBSplitBrainReporterState, A]

  def props(
      downer: ActorRef,
      stableAfter: FiniteDuration,
      downAllWhenUnstable: Option[FiniteDuration]
  ): Props =
    Props(new SBSplitBrainReporter(downer, stableAfter, downAllWhenUnstable))

  final private case object ClusterIsStable
  final private case object ClusterIsUnstable

  private[sbr] sealed abstract class NodeReachabilityEvent {
    def node: UniqueAddress
  }

  private[sbr] final case class NodeReachable(node: UniqueAddress) extends NodeReachabilityEvent

  private[sbr] final case class NodeIndirectlyConnected(node: UniqueAddress)
      extends NodeReachabilityEvent

  private[sbr] final case class NodeUnreachable(node: UniqueAddress) extends NodeReachabilityEvent

  /**
    * Information on the difference between two world views.
    *
    * @param changeIsStable true if both world views are the same ignoring `Joining` and `WeaklyUp` members.
    * @param hasNewUnreachableOrIndirectlyConnected true if the updated world view has more unreachable or indirectly
    *                                               connected nodes.
    */
  private[splitbrain] sealed abstract case class DiffInfo(
      changeIsStable: Boolean,
      hasNewUnreachableOrIndirectlyConnected: Boolean
  )

  private[splitbrain] object DiffInfo {
    def apply(oldWorldView: WorldView, updatedWorldView: WorldView): DiffInfo = {
      def isNonJoining(member: Member): Boolean =
        member.status =!= Joining && member.status =!= WeaklyUp

      // Remove members that are `Joining` or `WeaklyUp` as they
      // can appear during a split-brain.
      def nonJoining[N <: Node](nodes: SortedSet[N]): SortedSet[Member] =
        nodes.collect {
          case ReachableNode(member) if isNonJoining(member)           => member
          case UnreachableNode(member) if isNonJoining(member)         => member
          case IndirectlyConnectedNode(member) if isNonJoining(member) => member
        }

      /**
        * True if the both sets contain the same members with the same status.
        **/
      def noChange(members1: SortedSet[Member], members2: SortedSet[Member]): Boolean =
        members1.size === members2.size && members1.iterator.zip(members2.iterator).forall {
          case (member1, member2) =>
            member1.uniqueAddress === member2.uniqueAddress && member1.status === member2.status
        }

      val oldReachable = nonJoining(oldWorldView.reachableNodes)
      val oldIndirectlyConnected = nonJoining(oldWorldView.indirectlyConnectedNodes)
      val oldUnreachable = nonJoining(oldWorldView.unreachableNodes)

      val updatedReachable = nonJoining(updatedWorldView.reachableNodes)
      val updatedIndirectlyConnected = nonJoining(updatedWorldView.indirectlyConnectedNodes)
      val updatedUnreachable = nonJoining(updatedWorldView.unreachableNodes)

      val stableReachable = noChange(oldReachable, updatedReachable)
      val stableIndirectlyConnected = noChange(oldIndirectlyConnected, updatedIndirectlyConnected)
      val stableUnreachable = noChange(oldUnreachable, updatedUnreachable)

      val increase =
        oldIndirectlyConnected.size < updatedIndirectlyConnected.size || oldUnreachable.size < updatedUnreachable.size

      new DiffInfo(stableReachable && stableIndirectlyConnected && stableUnreachable, increase) {}
    }
  }
}
