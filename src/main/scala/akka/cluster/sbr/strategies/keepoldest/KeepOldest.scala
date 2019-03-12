package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.sbr._
import cats.data.NonEmptySet
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

final case class KeepOldest()

object KeepOldest {

  /**
   * The config for the [[KeepOldest]] strategy.
   *
   * @param downIfAlone down the oldest node if it is partitioned from all other nodes.
   */
  final case class Config(downIfAlone: Boolean)

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  }

  def keepOldest(worldView: WorldView, config: Config): Either[Error.type, StrategyDecision] =
    KeepOldestView(worldView, config).map {
      case OldestReachable =>
        NonEmptySet.fromSet(worldView.unreachableNodes).fold[StrategyDecision](Idle)(DownUnreachable)
      case OldestAlone =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable)
      case OldestUnreachable =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable)
    }

  implicit val keepOldestStrategy: Strategy.Aux[KeepOldest, Config] = new Strategy[KeepOldest] {
    override type Config = KeepOldest.Config
    override val name: String = "keep-oldest"
    override def handle(worldView: WorldView, config: KeepOldest.Config): Either[Throwable, StrategyDecision] =
      keepOldest(worldView, config)
  }
}
