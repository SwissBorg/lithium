package com.swissborg.sbr.strategy.keepoldest

import akka.cluster.Member
import cats.ApplicativeError
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.StrategyDecision._
import com.swissborg.sbr.strategy.keepoldest.KeepOldest.Config
import com.swissborg.sbr.strategy.{Strategy, StrategyDecision, StrategyReader}

/**
  * Split-brain resolver strategy that will keep the partition containing the oldest node and down
  * the other ones. By enabling `config.downIfAlone`, if the oldest node is alone (filtered by
  * `config.role`) it will down itself and keep the other partition.
  *
  * This strategy is useful when you are trying do not want the singleton instances to be migrated
  * after a resolution. The oldest node in the cluster contains the current singleton instance.
  */
private[sbr] class KeepOldest[F[_]: ApplicativeError[?[_], Throwable]](config: Config)
    extends Strategy[F] {
  import config._

  override def takeDecision(worldView: WorldView): F[StrategyDecision] = {
    val consideredNodes = worldView.consideredNodesWithRole(role)
    val allNodesSortedByAge = consideredNodes.toList.sortBy(_.member)(Member.ageOrdering)

    lazy val consideredCleanNodes = worldView.consideredCleanNodesWithRole(role)

    // If there are no nodes in the cluster with the given role the current partition is downed.
    allNodesSortedByAge.headOption.fold(downReachable(worldView).pure[F]) {
      case _: ReachableNode =>
        val decision = if (downIfAlone) {
          if (consideredCleanNodes.size === 1) {
            // The oldest is the only node in the cluster.
            // Ignoring indirectly-connected nodes as they will downed.
            // In this case we make the decision to not down
            // the cluster even if the oldest is alone.
            idle
          } else if (worldView.consideredReachableNodesWithRole(role).size === 1 &&
                     worldView.indirectlyConnectedNodesWithRole(role).isEmpty) {
            // The oldest node is seen as cut off from the rest of the cluster
            // from the partitions. The other partitions cannot see the indirectly
            // connected nodes in this partition and think they exist so they have
            // to be counted.
            DownReachable(worldView)
          } else {
            // The oldest node is not alone from the point of view of the other
            // partitions.
            downUnreachable(worldView)
          }
        } else {
          downUnreachable(worldView)
        }

        decision.pure[F]

      case _: UnreachableNode =>
        val decision = if (downIfAlone) {
          if (consideredCleanNodes.size === 1) {
            // The oldest is the only node in the cluster.
            // Ignoring indirectly-connected nodes as they will downed.
            // This decision should never be triggered but
            // it is left here just in case. Else the cluster
            // could down itself unnecessarily.
            downReachable(worldView)
          } else if (worldView.consideredUnreachableNodesWithRole(role).size === 1) {
            // The oldest node is cut off from the rest of the cluster.
            DownUnreachable(worldView)
          } else {
            // We cannot safely say that the oldest node is alone.
            // There might be more than two partitions but this cannot
            // be known within a partition.
            downReachable(worldView)
          }
        } else {
          downReachable(worldView)
        }

        decision.pure[F]

      case _: IndirectlyConnectedNode =>
        // Will be downed by the indirectly connected strategy.
        idle.pure[F]
    }
  }
}

private[sbr] object KeepOldest {

  /**
    * [[KeepOldest]] configuration.
    *
    * @param downIfAlone down the oldest node if it is cutoff from all the nodes with the given role.
    * @param role the role of the nodes to take in account.
    */
  final case class Config(downIfAlone: Boolean, role: String)
  object Config extends StrategyReader[Config] {
    override val name: String = "keep-oldest"
  }
}
