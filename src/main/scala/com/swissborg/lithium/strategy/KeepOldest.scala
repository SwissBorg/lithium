package com.swissborg.lithium

package strategy

import akka.cluster.Member
import akka.cluster.MemberStatus._
import cats._
import cats.implicits._
import com.swissborg.lithium.implicits._

/**
 * Split-brain resolver strategy that will keep the partition containing the oldest node and down
 * the other ones. By enabling `config.downIfAlone`, if the oldest node is alone (filtered by
 * `config.role`) it will down itself and keep the other partition.
 *
 * This strategy is useful when you are trying do not want the singleton instances to be migrated
 * after a resolution. The oldest node in the cluster contains the current singleton instance.
 */
private[lithium] class KeepOldest[F[_]: ApplicativeError[*[_], Throwable]](config: KeepOldest.Config)
    extends Strategy[F] {

  import config._

  override def takeDecision(worldView: WorldView): F[Decision] = {
    val allConsideredNodes =
      worldView.nonICNodesWithRole(role).filter(n => n.status === Up || n.status === Leaving)

    val allConsideredNodesSortedByAge = allConsideredNodes.toList.sortBy(_.member)(Member.ageOrdering)

    // If there are no nodes in the cluster with the given role the current partition is downed.
    allConsideredNodesSortedByAge.headOption
      .fold(Decision.downReachable(worldView)) {
        case node: ReachableNode =>
          if (node.status === Leaving) {
            // Nodes can change their status at the same time as a partition. This is especially problematic when the
            // oldest node becomes exiting. The side that sees the oldest node as leaving considers it and will decide
            // to down the other side. However, the other side sees it as exiting, doesn't consider it and decides
            // to survive because, by chance, contains the "new" oldest node. To counter this, the oldest node, if
            // leaving, will be assumed to have move to exiting on the other side. If it's really the
            // case, this will prevent both sides from downing each other and creating a split-brain. On the other,
            // if the other side didn't see the oldest node as exiting, both side might down themselves and down the
            // entire cluster. Better be safe than sorry.
            Decision.downReachable(worldView)
          } else {
            if (downIfAlone) {
              val nbrOfConsideredReachableNodes =
                allConsideredNodes.count {
                  case _: ReachableNode => true
                  case _                => false
                }

              if (nbrOfConsideredReachableNodes > 1) {
                Decision.downUnreachable(worldView)
              } else {
                Decision.downReachable(worldView)
              }
            } else {
              Decision.downUnreachable(worldView)
            }
          }

        case node: UnreachableNode =>
          if (node.status === Leaving) {
            // See comment above
            Decision.downReachable(worldView)
          } else {
            if (downIfAlone) {
              val nbrOfConsideredUnreachableNodes = worldView.unreachableNodesWithRole(role).size

              if (nbrOfConsideredUnreachableNodes > 1) {
                Decision.downReachable(worldView)
              } else {
                Decision.downUnreachable(worldView) // down the oldest node + all non-considered unreachable nodes
              }
            } else {
              Decision.downReachable(worldView)
            }
          }
      }
      .pure[F]
  }

  override def toString: String = s"KeepOldest($config)"
}

private[lithium] object KeepOldest {

  /**
   * [[KeepOldest]] configuration.
   *
   * @param downIfAlone down the oldest node if it is cutoff from all the nodes with the given role.
   * @param role        the role of the nodes to take in account.
   */
  final case class Config(downIfAlone: Boolean, role: String)

  object Config extends StrategyReader[Config] {
    override val name: String = "keep-oldest"
  }

}
