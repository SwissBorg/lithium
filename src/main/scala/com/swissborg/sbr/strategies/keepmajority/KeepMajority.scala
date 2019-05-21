package com.swissborg.sbr.strategies.keepmajority

import akka.cluster.Member
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.{Strategy, StrategyReader}

/**
 * Represents the "Keep Majority" split-brain resolution strategy.
 *
 * @param role the role of nodes to take in account.
 */
final case class KeepMajority(role: String) extends Strategy {
  import KeepMajority._

  /**
   * Strategy that will down a partition if it is not a majority. In case of an even number of nodes
   * it will choose the partition with the lowest address.
   *
   * A `role` can be provided to only take in account the nodes with that role in the decisions.
   * This can be useful if there are nodes that are more important than others.
   */
  override def takeDecision(worldView: WorldView): SyncIO[StrategyDecision] = {
    val totalNodes = worldView.consideredNodesWithRole(role).size

    val majority = Math.max(totalNodes / 2 + 1, 1)

    val reachableConsideredNodes   = worldView.consideredReachableNodesWithRole(role)
    val unreachableConsideredNodes = worldView.consideredUnreachableNodesWithRole(role)

    if (reachableConsideredNodes.size >= majority) {
      DownUnreachable(worldView).pure[SyncIO]
    } else if (unreachableConsideredNodes.size >= majority)
      DownReachable(worldView).pure[SyncIO]
    else if (totalNodes > 0 && reachableConsideredNodes.size === unreachableConsideredNodes.size) {
      // check if the node with the lowest address is in this partition
      worldView
        .consideredNodesWithRole(role)
        .toList
        .sortBy(_.member.address)(Member.addressOrdering)
        .headOption
        .fold[SyncIO[StrategyDecision]](NoMajority.raiseError[SyncIO, StrategyDecision]) {
          case _: ReachableNode   => DownUnreachable(worldView).pure[SyncIO]
          case _: UnreachableNode => DownReachable(worldView).pure[SyncIO]
          case _: IndirectlyConnectedNode =>
            new IllegalStateException("No indirectly connected node should be considered")
              .raiseError[SyncIO, StrategyDecision]
        }
    } else {
      // There are no nodes with the configured role in the cluster so
      // there is no partition with a majority. In this case we make
      // the safe decision to down the current partition.
      DownReachable(worldView).pure[SyncIO]
    }
  }
}

object KeepMajority extends StrategyReader[KeepMajority] {
  override val name: String = "keep-majority"

  case object NoMajority extends Throwable
}
