package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr._
import akka.cluster.sbr.strategies.staticquorum.StaticQuorum.TooManyNodes
import akka.cluster.sbr.strategy.{Strategy, StrategyReader}
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._

final case class StaticQuorum(role: String, quorumSize: Int Refined Positive) extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    if (worldView.consideredNodesWithRole(role).size > quorumSize * 2 - 1) {
      TooManyNodes.asLeft
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
      }).asRight
    }
}

object StaticQuorum extends StrategyReader[StaticQuorum] {
  override val name: String = "static-quorum"
  case object TooManyNodes extends Throwable("Aborting decision. There are too many nodes in the cluster.")
}
