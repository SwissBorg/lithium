package com.swissborg.lithium

package reporter

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster._
import cats.data.StateT
import cats.effect.SyncIO
import cats.syntax.all._
import com.swissborg.lithium.reachability.ReachabilityStatus.Reachable
import com.swissborg.lithium.reachability._
import com.swissborg.lithium.resolver._

import scala.concurrent.duration._

/**
 * Actor reporting on split-brain events.
 *
 * @param splitBrainResolver the actor that resolves the split-brain scenarios.
 * @param stableAfter duration during which a cluster has to be stable before attempting to resolve a split-brain.
 * @param downAllWhenUnstable downs the current partition if it takes longer than the duration. Otherwise nothing.
 * @param trackIndirectlyConnectedNodes detects indirectly-connected nodes when enabled.
 */
private[lithium] class SplitBrainReporter(private val splitBrainResolver: ActorRef,
                                          private val stableAfter: FiniteDuration,
                                          private val downAllWhenUnstable: Option[FiniteDuration],
                                          private val trackIndirectlyConnectedNodes: Boolean)
    extends Actor
    with Stash
    with ActorLogging
    with Timers {
  import SplitBrainReporter._

  private val cluster    = Cluster(context.system)
  private val selfMember = cluster.selfMember

  if (trackIndirectlyConnectedNodes)
    context.actorOf(ReachabilityReporter.props(self), "reachability-reporter")

  override def receive: Receive = initializing

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def initializing: Receive = {
    case snapshot: CurrentClusterState =>
      unstashAll()
      context.become(active(SplitBrainReporterState.fromSnapshot(selfMember, snapshot)))

    case _ => stash()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def active(state: SplitBrainReporterState): Receive = {

    // Only received when `trackIndirectlyConnectedNodes` is true.

    case NodeReachable(node) =>
      context.become(active(withReachableNode(node).runS(state).unsafeRunSync()))

    case NodeUnreachable(node) =>
      context.become(active(withUnreachableNode(node).runS(state).unsafeRunSync()))

    case NodeIndirectlyConnected(node) =>
      context.become(active(withIndirectlyConnectedNode(node).runS(state).unsafeRunSync()))

    // ---

    // Only received when `trackIndirectlyConnectedNodes` is false.

    case ReachableMember(member) =>
      context.become(active(withReachableNode(member.uniqueAddress).runS(state).unsafeRunSync()))

    case UnreachableMember(member) =>
      context.become(active(withUnreachableNode(member.uniqueAddress).runS(state).unsafeRunSync()))

    // ---

    case e: MemberEvent =>
      context.become(active(updateMember(e).runS(state).unsafeRunSync()))

    case ClusterIsStable =>
      context.become(active(handleSplitBrain.runS(state).unsafeRunSync()))

    case ClusterIsUnstable =>
      context.become(active(downAll.runS(state).unsafeRunSync()))
  }

  /**
   * Modify the state using `f` and manage the stability timers.
   *
   * If the change describes a non-stable change of the world view
   * it will reset the `ClusterIsStable` timer. If the after applying
   * `f` the world view is not subject to a partition anymore, the
   * `ClusterIsUnstable` timer is cancelled. On the other hand, if the
   * partition worsened and the timer is not running, it is started.
   */
  private def modifyAndManageStability(update: SplitBrainReporterState => SplitBrainReporterState): F[Unit] =
    StateT.modifyF { state =>
      val updatedState = update(state)

      val diff = DiffInfo(state.worldView, updatedState.worldView)

      def cancelClusterIsUnstableIfSplitBrainResolved: SyncIO[Unit] =
        if (hasSplitBrain(state.worldView)) SyncIO.unit
        else cancelClusterIsUnstable

      def scheduleClusterIsUnstableIfSplitBrainWorsened: SyncIO[Unit] =
        if (diff.hasAdditionalConsideredNonReachableNodes) scheduleClusterIsUnstable
        else SyncIO.unit

      val resetClusterIsStableIfUnstable: SyncIO[Unit] =
        if (diff.changeIsStable) SyncIO.unit
        else resetClusterIsStable

      for {
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

  private def updateMember(e: MemberEvent): F[Unit] =
    modifyAndManageStability(_.updatedMember(e.member))

  private def withReachableNode(node: UniqueAddress): F[Unit] =
    for {
      _ <- modifyAndManageStability(_.withReachableNode(node))
      _ <- StateT.liftF(SyncIO(log.info("[{}] became reachable.", node.address)))
    } yield ()

  private def withUnreachableNode(node: UniqueAddress): F[Unit] =
    for {
      _ <- modifyAndManageStability(_.withUnreachableNode(node))
      _ <- StateT.liftF(SyncIO(log.warning("[{}] became unreachable.", node.address)))
    } yield ()

  private def withIndirectlyConnectedNode(node: UniqueAddress): F[Unit] =
    for {
      _ <- modifyAndManageStability(_.withIndirectlyConnectedNode(node))
      _ <- StateT.liftF(SyncIO(log.warning("[{}] became indirectly-connected.", node.address)))
    } yield ()

  private val scheduleClusterIsStable: SyncIO[Unit] =
    SyncIO(timers.startSingleTimer(ClusterIsStable, ClusterIsStable, stableAfter))

  private val cancelClusterIsStable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsStable))

  private val resetClusterIsStable: SyncIO[Unit] = cancelClusterIsStable >> scheduleClusterIsStable

  private val scheduleClusterIsUnstable: SyncIO[Unit] =
    downAllWhenUnstable.traverse_(d => SyncIO(timers.startSingleTimer(ClusterIsUnstable, ClusterIsUnstable, d)))

  private val cancelClusterIsUnstable: SyncIO[Unit] = SyncIO(timers.cancel(ClusterIsUnstable))

  private val clusterIsUnstableIsActive: SyncIO[Boolean] = SyncIO(timers.isTimerActive(ClusterIsUnstable))

  /**
   * Send the resolver the order to run a split-brain resolution.
   *
   * If there's not split-brain, does nothing.
   */
  private val handleSplitBrain: F[Unit] =
    for {
      // Cancel else the partition will be downed if it takes too long for
      // the split-brain to be resolved after `SBResolver` downs the nodes.
      _ <- StateT.liftF[SyncIO, SplitBrainReporterState, Unit](cancelClusterIsUnstable)
      _ <- ifSplitBrain(SplitBrainResolver.ResolveSplitBrain(_))
      _ <- StateT.liftF(scheduleClusterIsStable)
    } yield ()

  private val downAll: F[Unit] = for {
    _ <- StateT.liftF[SyncIO, SplitBrainReporterState, Unit](cancelClusterIsStable)
    _ <- ifSplitBrain(SplitBrainResolver.DownAll)
    _ <- StateT.liftF(scheduleClusterIsStable)
  } yield ()

  private def ifSplitBrain(event: WorldView => SplitBrainResolver.Event): F[Unit] =
    StateT.inspectF { state =>
      if (hasSplitBrain(state.worldView)) {
        SyncIO(splitBrainResolver ! event(state.worldView))
      } else {
        SyncIO.unit
      }
    }

  private def hasSplitBrain(worldView: WorldView): Boolean =
    (worldView.unreachableNodes ++ worldView.indirectlyConnectedNodes)
      .exists(n => !nonHinderingWhenUnreachableStatus.contains(n.status))

  override def preStart(): Unit = {
    if (trackIndirectlyConnectedNodes) {
      cluster.subscribe(self, InitialStateAsSnapshot, classOf[ClusterEvent.MemberEvent])
    } else {
      cluster.subscribe(self,
                        InitialStateAsSnapshot,
                        classOf[ClusterEvent.MemberEvent],
                        classOf[ClusterEvent.ReachabilityEvent])
    }

    scheduleClusterIsStable.unsafeRunSync()
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    timers.cancel(ClusterIsStable)
    timers.cancel(ClusterIsUnstable)
  }
}

private[lithium] object SplitBrainReporter {
  private type F[A] = StateT[SyncIO, SplitBrainReporterState, A]

  val nonFullyFledgedMemberStatus: Set[MemberStatus]       = Set(Joining, WeaklyUp)
  val nonHinderingWhenUnreachableStatus: Set[MemberStatus] = Set(Down, Exiting)

  def props(downer: ActorRef,
            stableAfter: FiniteDuration,
            downAllWhenUnstable: Option[FiniteDuration],
            trackIndirectlyConnected: Boolean): Props =
    Props(new SplitBrainReporter(downer, stableAfter, downAllWhenUnstable, trackIndirectlyConnected))

  final private case object ClusterIsStable
  final private case object ClusterIsUnstable

  sealed abstract private[lithium] class NodeReachabilityEvent extends Product with Serializable {
    def node: UniqueAddress
  }

  final private[lithium] case class NodeReachable(node: UniqueAddress) extends NodeReachabilityEvent

  final private[lithium] case class NodeIndirectlyConnected(node: UniqueAddress) extends NodeReachabilityEvent

  final private[lithium] case class NodeUnreachable(node: UniqueAddress) extends NodeReachabilityEvent

  /**
   * Information on the difference between two world views.
   *
   * @param changeIsStable true if the changed the topology of the partition
   * @param hasAdditionalConsideredNonReachableNodes true if the updated world view has more unreachable or indirectly
   *                                                 connected nodes.
   */
  sealed abstract private[reporter] case class DiffInfo(changeIsStable: Boolean,
                                                        hasAdditionalConsideredNonReachableNodes: Boolean)

  private[reporter] object DiffInfo {

    def apply(oldWorldView: WorldView, updatedWorldView: WorldView): DiffInfo = {
      def considered[N <: Node](nodes: Set[N]): Set[N] = nodes.filter { node =>
        val isReachable = updatedWorldView.status(node.uniqueAddress).exists(_ === Reachable)

        val isReachableConsideredNode = isReachable && !nonFullyFledgedMemberStatus.contains(node.status)

        val isNonReachableHinderingLeader =
          !isReachable &&
            !nonHinderingWhenUnreachableStatus.contains(node.status) // not automatically removed on convergence

        isReachableConsideredNode || isNonReachableHinderingLeader
      }

      /**
       * True if the both sets contain the same nodes with the same status.
        **/
      def noChange[N1 <: Node, N2 <: Node](nodes1: Set[N1], nodes2: Set[N2]): Boolean =
        nodes1.map(node => (node.uniqueAddress, node.status)) === nodes2.map(node => (node.uniqueAddress, node.status))

      val oldReachable           = considered(oldWorldView.reachableNodes)
      val oldIndirectlyConnected = considered(oldWorldView.indirectlyConnectedNodes)
      val oldUnreachable         = considered(oldWorldView.unreachableNodes)

      val updatedReachable           = considered(updatedWorldView.reachableNodes)
      val updatedIndirectlyConnected = considered(updatedWorldView.indirectlyConnectedNodes)
      val updatedUnreachable         = considered(updatedWorldView.unreachableNodes)

      val stableReachable           = noChange(oldReachable, updatedReachable)
      val stableIndirectlyConnected = noChange(oldIndirectlyConnected, updatedIndirectlyConnected)
      val stableUnreachable         = noChange(oldUnreachable, updatedUnreachable)

      val oldNonReachable = oldIndirectlyConnected.map(_.uniqueAddress) ++
        oldUnreachable.map(_.uniqueAddress)

      val updatedNonReachable = updatedIndirectlyConnected.map(_.uniqueAddress) ++
        updatedUnreachable.map(_.uniqueAddress)

      val hasAdditionalNonReachableNodes =
        !oldNonReachable.iterator.sameElements(updatedNonReachable.iterator) &&
          oldNonReachable.subsetOf(updatedNonReachable)

      new DiffInfo(stableReachable && stableIndirectlyConnected && stableUnreachable, hasAdditionalNonReachableNodes) {}
    }
  }
}
