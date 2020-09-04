package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus
import akka.cluster.MemberStatus._
import cats.Applicative
import cats.syntax.all._

/**
 * Split-brain strategy that will keep the partition containing the referee (`config.address`)
 * and down all the other partitions.
 * If the remaining partition has less than `config.downAllIfLessThanNodes` the cluster will
 * be downed.
 *
 * This strategy is useful when the cluster has a node that is critical to its operation.
 */
private[lithium] class KeepReferee[F[_]: Applicative](config: KeepRefereeConfig) extends Strategy[F] {

  import config._

  override def takeDecision(worldView: WorldView): F[Decision] =
    worldView.reachableNodes
      .find(_.member.address.toString === referee)
      .fold(Decision.downReachable(worldView)) { _ =>
        val nbrOfConsideredReachableNodes = worldView.reachableNodes.count { node =>
          Set[MemberStatus](Up, Leaving).contains(node.member.status)
        }

        if (nbrOfConsideredReachableNodes < downAllIfLessThanNodes)
          Decision.downReachable(worldView)
        else
          Decision.downUnreachable(worldView)
      }
      .pure[F]

  override def toString: String = s"KeepReferee($config)"
}
