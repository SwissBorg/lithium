package com.swissborg.sbr.strategies.keepoldest

import akka.cluster.Member
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.{Strategy, StrategyReader}

/**
 * Strategy that will down a partition if it does NOT contain the oldest node.
 *
 * A `role` can be provided (@see [[com.swissborg.sbr.strategies.keepoldest.KeepOldest]]
 * to only take in account the nodes with that role in the decision. This can be useful if there
 * are nodes that are more important than others.
 */
final case class KeepOldest(downIfAlone: Boolean, role: String) extends Strategy {
  override def takeDecision(worldView: WorldView): SyncIO[StrategyDecision] = {
    val consideredNodes     = worldView.consideredNodesWithRole(role)
    val allNodesSortedByAge = consideredNodes.toList.sortBy(_.member)(Member.ageOrdering)

    // If there are no nodes in the cluster with the given role the current partition is downed.
    allNodesSortedByAge.headOption.fold[SyncIO[StrategyDecision]](DownReachable(worldView).pure[SyncIO]) {
      case _: ReachableNode =>
        if (downIfAlone) {
          if (consideredNodes.size === 1) {
            // The oldest is the only node in the cluster.
            Idle.pure[SyncIO]
          } else if (worldView.consideredReachableNodesWithRole(role).size === 1 &&
                     worldView.indirectlyConnectedNodesWithRole(role).isEmpty) {
            // The oldest node is seen as cut off from the rest of the cluster
            // from the partitions. The other partitions cannot see the indirectly
            // connected nodes in this partition and think they exist so they have
            // to be counted.
            DownReachable(worldView).pure[SyncIO]
          } else {
            // The oldest node is not alone from the point of view of the other
            // partitions.
            DownUnreachable(worldView).pure[SyncIO]
          }
        } else {
          DownUnreachable(worldView).pure[SyncIO]
        }

      case _: UnreachableNode =>
        if (downIfAlone) {
          if (consideredNodes.size === 1) {
            // The oldest is the only node in the cluster.
            // This decision should never be triggered but
            // it is left here just in case. Else the cluster
            // could down itself unnecessarily.
            DownReachable(worldView).pure[SyncIO]
          } else if (worldView.consideredUnreachableNodesWithRole(role).size === 1) {
            // The oldest node is cut off from the rest of the cluster.
            DownUnreachable(worldView).pure[SyncIO]
          } else {
            // The oldest node is not alone
            DownReachable(worldView).pure[SyncIO]
          }
        } else {
          DownReachable(worldView).pure[SyncIO]
        }

      case _: IndirectlyConnectedNode =>
        new IllegalStateException("Indirectly connected nodes should not be considered")
          .raiseError[SyncIO, StrategyDecision]
    }
  }
}

object KeepOldest extends StrategyReader[KeepOldest] {
  override val name: String = "keep-oldest"
}
