package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.Member
import akka.cluster.sbr._
import akka.cluster.sbr.strategy.{Strategy, StrategyReader}
import cats.implicits._

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
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] = {
    val totalNodes = worldView.consideredNodesWithRole(role).size

    val majority =
      if (totalNodes <= 0) {
        // makes sure that the partition will always down itself
        1
      } else {
        totalNodes / 2 + 1
      }

    val reachableConsideredNodes   = worldView.consideredReachableNodesWithRole(role)
    val unreachableConsideredNodes = worldView.consideredUnreachableNodesWithRole(role)

    if (reachableConsideredNodes.size >= majority) {
      DownUnreachable(worldView).asRight
    } else if (unreachableConsideredNodes.size >= majority)
      DownReachable(worldView).asRight
    else if (reachableConsideredNodes.size === unreachableConsideredNodes.size) {
      // check if the node with the lowest address is in this partition
      worldView
        .consideredNodesWithRole(role)
        .toList
        .sortBy(_.member.address)(Member.addressOrdering)
        .headOption
        .fold[Either[Throwable, StrategyDecision]](NoMajority.asLeft) {
          case _: ReachableNode   => DownUnreachable(worldView).asRight
          case _: UnreachableNode => DownReachable(worldView).asRight
          case _: IndirectlyConnectedNode =>
            new IllegalStateException("No indirectly connected node should be considered").asLeft
        }
    } else NoMajority.asLeft
  }
}

object KeepMajority extends StrategyReader[KeepMajority] {
  override val name: String = "keep-majority"

  case object NoMajority extends Throwable
}
