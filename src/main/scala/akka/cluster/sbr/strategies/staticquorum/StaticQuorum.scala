package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr._
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._
import eu.timepit.refined.pureconfig._ // DO NOT REMOVE

final case class StaticQuorum()

object StaticQuorum {
  final case class Config(quorumSize: QuorumSize)

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  }

  def staticQuorum(worldView: WorldView, config: Config): Either[Throwable, StrategyDecision] =
    ReachableNodes(worldView, config.quorumSize).map { reachableNodes =>
      (reachableNodes, UnreachableNodes(worldView, config.quorumSize)) match {

        /**
         * If we decide DownReachable the entire cluster will shutdown. Always?
         * If we decide DownUnreachable we might create a SB if there's actually quorum in the unreachable
         *
         * Either way this happens when `quorumSize` is less than half of the cluster. That SHOULD be logged! TODO
         */
        case (reachableQuorum: ReachableQuorum, _: StaticQuorumUnreachablePotentialQuorum) =>
          // Idle
          UnsafeDownReachable(reachableQuorum.reachableNodes)

        /**
         * This side is the quorum, the other side should be downed.
         */
        case (_: ReachableQuorum, subQuorum: StaticQuorumUnreachableSubQuorum) =>
          DownUnreachable(subQuorum.unreachableNodes)

        /**
         * This side is a query and there are no unreachable nodes, nothing needs to be done.
         */
        case (_: ReachableQuorum, _: EmptyUnreachable) => Idle

        /**
         * Potentially shuts down the cluster if there's
         * no quorum on the other side of the split.
         */
        case (subQuorum: ReachableSubQuorum, _: StaticQuorumUnreachablePotentialQuorum) =>
          DownReachable(subQuorum.reachableNodes) // TODO create unsafe version?

        /**
         * Both sides are not a quorum.
         *
         * Happens when to many nodes crash at the same time. The cluster will shutdown.
         */
        case (subQuorum: ReachableSubQuorum, _) => DownReachable(subQuorum.reachableNodes)
      }
    }

  implicit val staticQuorumStrategy: Strategy.Aux[StaticQuorum, StaticQuorum.Config] = new Strategy[StaticQuorum] {
    override type Config = StaticQuorum.Config
    override val name: String = "quorum-size"
    override def handle(worldView: WorldView, config: Config): Either[Throwable, StrategyDecision] =
      staticQuorum(worldView, config)
  }
}
