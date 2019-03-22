package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr._
import akka.cluster.sbr.strategies.keepmajority.NodesMajority.{NoMajority, NodesMajorityError}
import cats.implicits._
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

final case class KeepMajority()

object KeepMajority {
  final case class Config(role: String)

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  }

  def keepMajority(worldView: WorldView, config: Config): Either[NodesMajorityError, StrategyDecision] =
    NodesMajority(worldView, config.role)
      .map {
        case ReachableMajority | ReachableLowestAddress     => DownUnreachable(worldView)
        case UnreachableMajority | UnreachableLowestAddress => DownReachable(worldView)
      }
      .recoverWith {
        case NoMajority => DownReachable(worldView).asRight
      }

  implicit val keepMajorityStrategy: Strategy.Aux[KeepMajority, KeepMajority.Config] = new Strategy[KeepMajority] {
    override type Config = KeepMajority.Config
    override val name: String = "keep-majority"
    override def handle(worldView: WorldView, config: KeepMajority.Config): Either[Throwable, StrategyDecision] =
      keepMajority(worldView, config)
  }
}
