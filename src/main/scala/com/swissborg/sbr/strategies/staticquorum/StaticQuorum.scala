package com.swissborg.sbr.strategies.staticquorum

import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr.{DownReachable, _}
import com.swissborg.sbr.strategy.{Strategy, StrategyReader}
import com.typesafe.scalalogging.StrictLogging
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._

final case class StaticQuorum(role: String, quorumSize: Int Refined Positive) extends Strategy with StrictLogging {
  override def takeDecision(worldView: WorldView): SyncIO[StrategyDecision] =
    if (worldView.consideredNodesWithRole(role).size > quorumSize * 2 - 1) {
      // The quorumSize is too small for the cluster size,
      // more than one side might be a quorum and create
      // a split-brain. In this case we down the cluster.
      SyncIO(
        logger.warn("The configured quorum-size ({}) is too small!", quorumSize)
      ).as(DownReachable(worldView))
    } else {
      ((ReachableNodes(worldView, quorumSize, role), UnreachableNodes(worldView, quorumSize, role)) match {

        /**
         * If we decide DownReachable the entire cluster will shutdown. Always?
         * If we decide DownUnreachable we might create a SB if there's actually quorum in the unreachable
         *
         * Either way this happens when `quorumSize` is less than half of the cluster. That SHOULD be logged! TODO
         */
        case (ReachableQuorum, UnreachablePotentialQuorum) => DownReachable(worldView)

        /**
         * This side is the quorum, the other side should be downed.
         */
        case (ReachableQuorum, UnreachableSubQuorum) => DownUnreachable(worldView)

        /**
         * This side is a quorum and there are no unreachable nodes, nothing needs to be done.
         */
        case (ReachableQuorum, EmptyUnreachable) => Idle

        /**
         * Potentially shuts down the cluster if there's
         * no quorum on the other side of the split.
         */
        case (ReachableSubQuorum, UnreachablePotentialQuorum) => DownReachable(worldView)

        /**
         * Both sides are not a quorum.
         *
         * Happens when to many nodes crash at the same time. The cluster will shutdown.
         */
        case (ReachableSubQuorum, _) => DownReachable(worldView)
      }).pure[SyncIO]
    }
}

object StaticQuorum extends StrategyReader[StaticQuorum] {
  override val name: String = "static-quorum"
}
