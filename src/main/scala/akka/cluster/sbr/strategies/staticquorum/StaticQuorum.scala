package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.Strategy

final case class StaticQuorum(role: String, quorumSize: QuorumSize)

object StaticQuorum {
  def staticQuorum(strategy: StaticQuorum, worldView: WorldView): Either[Throwable, StrategyDecision] =
    ReachableNodes(worldView, strategy.quorumSize, strategy.role).map { reachableNodes =>
      val a = (reachableNodes, UnreachableNodes(worldView, strategy.quorumSize, strategy.role))

      a match {

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
         * This side is a query and there are no unreachable nodes, nothing needs to be done.
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
      }
    }

  implicit val staticQuorumStrategy: Strategy[StaticQuorum] = new Strategy[StaticQuorum] {
    override def takeDecision(strategy: StaticQuorum, worldView: WorldView): Either[Throwable, StrategyDecision] =
      staticQuorum(strategy, worldView)
  }

  implicit val staticQuorumStrategyReader: StrategyReader[StaticQuorum] = StrategyReader.fromName("static-quorum")
//  implicit val staticQuorumReader: ConfigReader[StaticQuorum] = deriveReader[StaticQuorum]
}
