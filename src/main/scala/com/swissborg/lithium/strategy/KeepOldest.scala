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
private[lithium] class KeepOldest[F[_]: ApplicativeError[?[_], Throwable]](
    config: KeepOldest.Config
) extends Strategy[F] {

  import config._

  override def takeDecision(worldView: WorldView): F[Decision] = {
    // Leaving nodes are not counted as they might have moved to "removed"
    // on the other side and thus not considered.
    val consideredNonICNodes =
      worldView
        .nonICNodesWithRole(role)
        .filter(n => n.status === Up || n.status === Leaving)

    val allNodesSortedByAge = consideredNonICNodes.toList.sortBy(_.member)(Member.ageOrdering)

    // If there are no nodes in the cluster with the given role the current partition is downed.
    allNodesSortedByAge.headOption
      .fold(Decision.downReachable(worldView)) {
        case _: ReachableNode =>
          if (downIfAlone) {
            val nbrOfConsideredReachableNodes =
              consideredNonICNodes.count {
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

        case _: UnreachableNode =>
          if (downIfAlone) {
            val nbrOfConsideredUnreachableNodes = worldView.unreachableNodesWithRole(role).size

            if (nbrOfConsideredUnreachableNodes > 1) {
              Decision.downReachable(worldView)
            } else {
              Decision.downUnreachable(worldView)
            }
          } else {
            Decision.downReachable(worldView)
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
