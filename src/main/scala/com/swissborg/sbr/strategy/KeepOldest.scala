package com.swissborg.sbr
package strategy

import akka.cluster.Member
import cats._
import cats.implicits._

/**
  * Split-brain resolver strategy that will keep the partition containing the oldest node and down
  * the other ones. By enabling `config.downIfAlone`, if the oldest node is alone (filtered by
  * `config.role`) it will down itself and keep the other partition.
  *
  * This strategy is useful when you are trying do not want the singleton instances to be migrated
  * after a resolution. The oldest node in the cluster contains the current singleton instance.
  */
private[sbr] class KeepOldest[F[_]: ApplicativeError[?[_], Throwable]](config: KeepOldest.Config)
    extends Strategy[F] {
  import config._

  override def takeDecision(worldView: WorldView): F[Decision] = {
    val consideredNonICNodes = worldView.nonJoiningNonICNodesWithRole(role)
    val allNodesSortedByAge = consideredNonICNodes.toList.sortBy(_.member)(Member.ageOrdering)

    println(s"SORTED ${allNodesSortedByAge}")

    // If there are no nodes in the cluster with the given role the current partition is downed.
    allNodesSortedByAge.headOption
      .fold(Decision.downReachable(worldView)) {
        case oldest: ReachableNode =>
          println(s"R $oldest")
          if (downIfAlone) {
            if (worldView.nonJoiningReachableNodesWithRole(role).size > 1) {
              // The indirectly-connected nodes are also taken in account
              // as they are seen as unreachable from the other partitions.
              Decision.downUnreachable(worldView)
            } else {
              Decision.downReachable(worldView)
            }
          } else {
            Decision.downUnreachable(worldView)
          }

        case oldest: UnreachableNode =>
          println(s"U $oldest")
          if (downIfAlone) {
            if (worldView.unreachableNodesWithRole(role).size > 1) {
              // The oldest node is not alone.
              //
              // Also looks at joining nodes. If one of the unreachable
              // nodes moved to up during the partition and the change
              // was not seen by this partition the strategy might create
              // a split-brain. By assuming it was the problem is solved.
              // However, this can lead to the cluster being downed
              // unnecessarily if the unreachable joining nodes did
              // not move to up.
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
