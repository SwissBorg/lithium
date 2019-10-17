package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus._
import cats.ApplicativeError
import cats.implicits._
import com.swissborg.lithium.implicits._

/**
 * Split-brain resolver strategy that will keep the partition containing more than half of the nodes and down the other
 * ones. In case of an even number and nodes and none is a majority the partition containing the node
 * if the lowest address will be picked as a survivor.
 *
 * This strategy is useful when the cluster is dynamic.
 */
private[lithium] class KeepMajority[F[_]: ApplicativeError[*[_], Throwable]](config: KeepMajority.Config)
    extends Strategy[F] {

  import config._

  override def takeDecision(worldView: WorldView): F[Decision] = {
    // Leaving nodes are ignored as they might have changed to
    // to "exiting" on the non-reachable side.

    val allNodes   = worldView.nonICNodesWithRole(role).filter(n => n.status === Up || n.status === Leaving)
    val totalNodes = allNodes.size

    val majority = Math.max(totalNodes / 2 + 1, 1)

    val reachableConsideredNodes = allNodes.collect {
      case node: ReachableNode => node
    }

    val unreachableConsideredNodes = allNodes.collect {
      case node: UnreachableNode => node
    }

    if (reachableConsideredNodes.size >= majority) {
      Decision.downUnreachable(worldView).pure[F]
    } else if (unreachableConsideredNodes.size >= majority)
      Decision.downReachable(worldView).pure[F]
    else if (totalNodes > 0 && reachableConsideredNodes.size === unreachableConsideredNodes.size) {
      // check if the node with the lowest unique address is in this partition
      allNodes.toList.sorted.headOption.fold(KeepMajority.NoMajority.raiseError[F, Decision]) {
        case _: ReachableNode   => Decision.downUnreachable(worldView).pure[F]
        case _: UnreachableNode => Decision.downReachable(worldView).pure[F]
      }
    } else {
      // There are no nodes with the configured role in the cluster so
      // there is no partition with a majority. In this case we make
      // the safe decision to down the current partition.
      Decision.downReachable(worldView).pure[F]
    }
  }

  override def toString: String = s"KeepMajority($config)"
}

private[lithium] object KeepMajority {

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
