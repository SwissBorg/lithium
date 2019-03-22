package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.sbr._
import akka.cluster.sbr.strategies.keepoldest.KeepOldestView.KeepOldestViewError
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

final case class KeepOldest()

object KeepOldest {

  /**
   * The config for the [[KeepOldest]] strategy.
   *
   * @param downIfAlone down the oldest node if it is partitioned from all other nodes.
   */
  final case class Config(downIfAlone: Boolean, role: String)

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  }

  def keepOldest(worldView: WorldView, config: Config): Either[KeepOldestViewError, StrategyDecision] =
    KeepOldestView(worldView, config.downIfAlone, config.role).map {
      case OldestReachable                 => DownUnreachable(worldView)
      case OldestAlone | OldestUnreachable => DownReachable(worldView)
    }

  implicit val keepOldestStrategy: Strategy.Aux[KeepOldest, Config] = new Strategy[KeepOldest] {
    override type Config = KeepOldest.Config
    override val name: String = "keep-oldest"
    override def handle(worldView: WorldView, config: KeepOldest.Config): Either[Throwable, StrategyDecision] =
      keepOldest(worldView, config)
  }
}
