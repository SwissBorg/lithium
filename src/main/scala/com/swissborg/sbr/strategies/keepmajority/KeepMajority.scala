package com.swissborg.sbr.strategies.keepmajority

import akka.cluster.Member
import cats.ApplicativeError
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategies.keepmajority.KeepMajority.Config
import com.swissborg.sbr.strategy.{Strategy, StrategyReader}

/**
 * Split-brain resolver strategy that will keep the partition containing more than half of the nodes and down the other
 * ones. In case of an even number and nodes and none is a majority the partition containing the node
 * if the lowest address will be picked as a survivor.
 *
 * This strategy is useful when the cluster is dynamic.
 */
class KeepMajority[F[_]: ApplicativeError[?[_], Throwable]](config: Config) extends Strategy[F] {
  import KeepMajority._
  import config._

  override def takeDecision(worldView: WorldView): F[StrategyDecision] = {
    val totalNodes = worldView.consideredNodesWithRole(role).size

    val majority = Math.max(totalNodes / 2 + 1, 1)

    val reachableConsideredNodes   = worldView.consideredReachableNodesWithRole(role)
    val unreachableConsideredNodes = worldView.consideredUnreachableNodesWithRole(role)

    if (reachableConsideredNodes.size >= majority) {
      DownUnreachable(worldView).pure[F].widen
    } else if (unreachableConsideredNodes.size >= majority)
      DownReachable(worldView).pure[F].widen
    else if (totalNodes > 0 && reachableConsideredNodes.size === unreachableConsideredNodes.size) {
      // check if the node with the lowest address is in this partition
      worldView
        .consideredNodesWithRole(role)
        .toList
        .sortBy(_.member.address)(Member.addressOrdering)
        .headOption
        .fold(NoMajority.raiseError[F, StrategyDecision]) {
          case _: ReachableNode =>
            DownUnreachable(worldView).pure[F].widen

          case _: UnreachableNode =>
            DownReachable(worldView).pure[F].widen

          case _: IndirectlyConnectedNode =>
            new IllegalStateException("No indirectly connected node should be considered")
              .raiseError[F, StrategyDecision]
        }
    } else {
      // There are no nodes with the configured role in the cluster so
      // there is no partition with a majority. In this case we make
      // the safe decision to down the current partition.
      DownReachable(worldView).pure[F].widen
    }
  }
}

object KeepMajority {

  /**
   * [[KeepMajority]] configuration.
   *
   * @param role the role of the nodes to take in account.
   */
  final case class Config(role: String)
  object Config extends StrategyReader[Config] {
    override val name: String = "keep-majority"
  }

  case object NoMajority extends Throwable
}
