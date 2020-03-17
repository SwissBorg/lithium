package com.swissborg.lithium

package resolver

import akka.actor._
import akka.cluster._
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.lithium.implicits._
import com.swissborg.lithium.reporter._
import com.swissborg.lithium.strategy._

import scala.concurrent.duration._

/**
 * Actor resolving split-brain scenarios.
 *
 * It handles two events: `SBResolver.HandleSplitBrain` and `SBResolver.DownAll`,
 * both accompagnied with a `WorldView` describing a partitioned cluster.
 *
 * The `Resolver.HandleSplitBrain` event triggers the downing of the members
 * as described by the decision given by `Union(_strategy, IndirectlyConnected)`.
 *
 * The `Resolver.DownAll` event triggers the downing of all the nodes.
 *
 * @param _strategy                     the strategy with which to resolved the split-brain.
 * @param stableAfter                   duration during which a cluster has to be stable before attempting to resolve a split-brain.
 * @param downAllWhenUnstable           down the partition if the cluster has been unstable for longer than `stableAfter + 3/4 * stableAfter`.
 * @param trackIndirectlyConnectedNodes downs the detected indirectly-connected nodes when enabled.
 */
private[lithium] class SplitBrainResolver(private val _strategy: Strategy[SyncIO],
                                          private val stableAfter: FiniteDuration,
                                          private val downAllWhenUnstable: Option[FiniteDuration],
                                          private val trackIndirectlyConnectedNodes: Boolean)
    extends Actor
    with ActorLogging {
  context.actorOf(SplitBrainReporter.props(self, stableAfter, downAllWhenUnstable, trackIndirectlyConnectedNodes),
                  "split-brain-reporter")

  private val cluster: Cluster                 = Cluster(context.system)
  private val selfUniqueAddress: UniqueAddress = cluster.selfUniqueAddress
  private val selfMember: Member               = cluster.selfMember

  private val strategy: Union[SyncIO, Strategy, IndirectlyConnected] =
    new Union(_strategy, new IndirectlyConnected)

  private val downAll: DownAll[SyncIO] = new DownAll()

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def receive: Receive = {
    case SplitBrainResolver.ResolveSplitBrain(worldView) =>
      cluster.state.leader match {
        case Some(leader) if leader === selfUniqueAddress.address =>
          resolveSplitBrain(worldView, false).unsafeRunSync()

        case None =>
          // There is no leader, only down self if part of the nodes to down
          resolveSplitBrain(worldView, true).unsafeRunSync()

        case _ =>
          // member is not the leader, do nothing.
          log.debug("[{}}] is not the leader. The leader will handle the split-brain.", selfMember)
      }

    case SplitBrainResolver.DownAll(worldView) =>
      cluster.state.leader match {
        case Some(leader) if leader === selfUniqueAddress.address =>
          downAll(worldView, false).unsafeRunSync()

        case None =>
          // There is no leader, only down self if part of the nodes to down
          downAll(worldView, true).unsafeRunSync()

        case _ =>
          // member is not the leader, do nothing.
          log.debug("[{}}] is not the leader. The leader will down all the nodes.", selfMember)
      }
  }

  /**
   * Handle the partition using the [[Union]] of the configured
   * strategy and the [[IndirectlyConnected]].
   */
  private def resolveSplitBrain(worldView: WorldView, downSelfOnly: Boolean): SyncIO[Unit] =
    for {
      _ <- SyncIO(
        log.warning(
          """[{}] Received request to handle a split-brain...
            |-- Worldview --
            |Reachable nodes:
            |  {}
            |Unreachable nodes:
            |  {}
            |Indirectly-connected nodes:
            |  {}
            |""".stripMargin,
          selfMember,
          worldView.reachableNodes.mkString_("\n  "),
          worldView.unreachableNodes.mkString_("\n  "),
          worldView.indirectlyConnectedNodes.mkString_("\n  ")
        )
      )
      _ <- runStrategy(strategy, worldView, downSelfOnly)
    } yield ()

  /**
   * Handle the partition by downing all the members.
   */
  private def downAll(worldView: WorldView, downSelfOnly: Boolean): SyncIO[Unit] =
    for {
      _ <- SyncIO(
        log.warning(
          """[{}] Received request to down all the nodes...
            |-- Worldview --
            |Reachable nodes:
            |  {}
            |Unreachable nodes:
            |  {}
            |Indirectly-connected nodes:
            |  {}
            |""".stripMargin,
          selfMember,
          worldView.reachableNodes.mkString_("\n  "),
          worldView.unreachableNodes.mkString_("\n  "),
          worldView.indirectlyConnectedNodes.mkString_("\n  ")
        )
      )
      _ <- runStrategy(downAll, worldView, downSelfOnly)
    } yield ()

  /**
   * Run `strategy` on `worldView`.
   *
   * Enable `nonJoiningOnly` so that joining and weakly-up
   * members do not run the strategy.
   */
  private def runStrategy(strategy: Strategy[SyncIO], worldView: WorldView, downSelfOnly: Boolean): SyncIO[Unit] = {
    def execute(decision: Decision): SyncIO[Unit] = {

      val nodesToDown =
        if (downSelfOnly) decision.nodesToDown.filter(_.uniqueAddress === selfUniqueAddress)
        else decision.nodesToDown

      if (nodesToDown.nonEmpty) {
        for {
          _ <- SyncIO(
            log.warning(
              """[{}] Downing the nodes:
                |  {}{}
                |""".stripMargin,
              selfMember,
              nodesToDown.mkString_("\n  "),
              if (downSelfOnly) "\nNote: no leader, only the self node will be downed." else ""
            )
          )
          _ <- nodesToDown.toList.traverse_(node => SyncIO(cluster.down(node.address)))
        } yield ()
      } else {
        SyncIO(
          log.warning("[{}] No nodes to down. {}",
                      selfMember,
                      if (downSelfOnly) "\nNote: no leader, only the self node can be downed." else "")
        )
      }
    }

    strategy
      .takeDecision(worldView)
      .flatMap(execute)
      .handleErrorWith(
        err => SyncIO(log.error(err, "[{}] An error occurred during the resolution.", selfUniqueAddress))
      )
  }
}

object SplitBrainResolver {

  def props(strategy: Strategy[SyncIO],
            stableAfter: FiniteDuration,
            downAllWhenUnstable: Option[FiniteDuration],
            trackIndirectlyConnectedNodes: Boolean): Props =
    Props(new SplitBrainResolver(strategy, stableAfter, downAllWhenUnstable, trackIndirectlyConnectedNodes))

  sealed private[lithium] trait Event

  final private[lithium] case class ResolveSplitBrain(worldView: WorldView) extends Event

  final private[lithium] case class DownAll(worldView: WorldView) extends Event

}
