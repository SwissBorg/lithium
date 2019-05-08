package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.Member
import akka.cluster.sbr._
import akka.cluster.sbr.strategy.{Strategy, StrategyReader}
import cats.implicits._

/**
 * Strategy that will down a partition if it does NOT contain the oldest node.
 *
 * A `role` can be provided (@see [[akka.cluster.sbr.strategies.keepoldest.KeepOldest]]
 * to only take in account the nodes with that role in the decision. This can be useful if there
 * are nodes that are more important than others.
 *
 *
 */
final case class KeepOldest(downIfAlone: Boolean, role: String) extends Strategy {

  import KeepOldest._

  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] = {
    val consideredNodes     = worldView.consideredNodesWithRole(role)
    val allNodesSortedByAge = consideredNodes.toList.sortBy(_.member)(Member.ageOrdering)

    allNodesSortedByAge.headOption.fold[Either[Throwable, StrategyDecision]](NoOldestNode.asLeft) {
      case _: ReachableNode =>
        if (downIfAlone) {
          if (consideredNodes.size === 1) {
            // The oldest is the only node in the cluster..
            Idle.asRight
          } else if (worldView.consideredReachableNodesWithRole(role).size === 1) {
            // The oldest node is cut off from the rest of the cluster.
            DownReachable(worldView).asRight
          } else {
            // The oldest node is not alone
            DownUnreachable(worldView).asRight
          }
        } else {
          DownUnreachable(worldView).asRight
        }

      case _: UnreachableNode =>
        if (downIfAlone) {
          if (consideredNodes.size === 1) {
            // The oldest is the only node in the cluster.
            // This decision should never be triggered but
            // it is left here just in case. Else the cluster
            // could down itself unnecessarily.
            DownReachable(worldView).asRight
          } else if (worldView.consideredUnreachableNodesWithRole(role).size === 1) {
            // The oldest node is cut off from the rest of the cluster.
            DownUnreachable(worldView).asRight
          } else {
            // The oldest node is not alone
            DownReachable(worldView).asRight
          }
        } else {
          DownReachable(worldView).asRight
        }

      case _: IndirectlyConnectedNode =>
        new IllegalStateException("Indirectly connected nodes should not be considered").asLeft
    }
  }
}

object KeepOldest extends StrategyReader[KeepOldest] {
  override val name: String = "keep-oldest"

  final case object NoOldestNode extends Throwable
}
