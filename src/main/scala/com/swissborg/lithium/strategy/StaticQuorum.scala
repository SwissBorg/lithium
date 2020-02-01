package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus.{Leaving, Up}
import cats.effect.Sync
import cats.implicits._
import com.swissborg.lithium.implicits._
import org.slf4j.LoggerFactory

/**
 * Split-brain resolver strategy that will keep the partition that have a quorum (`config.quorumSize`) and down the other
 * ones. In case the multiple the quorum size is too small for the size of the cluster a warning will be logged and the
 * cluster downed.
 *
 * This strategy is useful when the cluster has a fixed size.
 */
private[lithium] class StaticQuorum[F[_]: Sync](config: StaticQuorumConfig) extends Strategy[F] {

  import config._

  private val logger = LoggerFactory.getLogger(this.getClass)

  override def takeDecision(worldView: WorldView): F[Decision] = {
    val nbrOfConsideredNonICNodes = worldView.nonICNodesWithRole(role).count { n =>
      n.status === Up || n.status === Leaving
    }

    if (nbrOfConsideredNonICNodes > quorumSize * 2 - 1) {
      // The quorumSize is too small for the cluster size,
      // more than one side might be a quorum and create
      // a split-brain. In this case we down the cluster.
      Sync[F]
        .delay(logger.warn("The configured quorum-size ({}) is too small!", quorumSize))
        .as(Decision.downReachable(worldView))
    } else {
      (ReachableQuorum(worldView, quorumSize, role), UnreachableQuorum(worldView, quorumSize, role)) match {

        /**
         * If we decide DownReachable the entire cluster will shutdown. Always?
         * If we decide DownUnreachable we might create a SB if there's actually quorum in the unreachable
         *
         * Either way this happens when `quorumSize` is less than half of the cluster. That SHOULD be logged! TODO
         */
        case (ReachableQuorum.Quorum, UnreachableQuorum.PotentialQuorum) =>
          Decision.downReachable(worldView)

        /**
         * This side is the quorum, the other side should be downed.
         */
        case (ReachableQuorum.Quorum, UnreachableQuorum.SubQuorum) =>
          Decision.downUnreachable(worldView)

        /**
         * This side is a quorum and there are no unreachable nodes,
         * still downing the unreachable nodes as they might be joining
         * so not counted.
         */
        case (ReachableQuorum.Quorum, UnreachableQuorum.None) =>
          Decision.downUnreachable(worldView)

        /**
         * Potentially shuts down the cluster if there's
         * no quorum on the other side of the split.
         */
        case (ReachableQuorum.NoQuorum, UnreachableQuorum.PotentialQuorum) =>
          Decision.downReachable(worldView)

        /**
         * Both sides are not a quorum.
         *
         * Happens when to many nodes crash at the same time. The cluster will shutdown.
         */
        case (ReachableQuorum.NoQuorum, _) => Decision.downReachable(worldView)
      }
    }.pure[F]
  }

  override def toString: String = s"StaticQuorum($config)"
}
