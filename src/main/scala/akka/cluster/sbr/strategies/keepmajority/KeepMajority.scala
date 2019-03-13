package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr._
import cats.data.NonEmptySet
import cats.implicits._
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

final case class KeepMajority()

object KeepMajority {
  final case class Config(role: String)

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  }

  def keepMajority(worldView: WorldView, config: Config): StrategyDecision =
    NodesMajority(worldView, config.role) match {
      case _: ReachableMajority | _: ReachableLowestAddress =>
        NonEmptySet.fromSet(worldView.unreachableNodes).fold[StrategyDecision](Idle)(DownUnreachable)

      case _: UnreachableMajority | _: UnreachableLowestAddress =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable)

      // Same as Akka
      case NoMajority =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable)
    }

  implicit val keepMajorityStrategy: Strategy.Aux[KeepMajority, KeepMajority.Config] = new Strategy[KeepMajority] {
    override type Config = KeepMajority.Config
    override val name: String = "keep-majority"
    override def handle(worldView: WorldView, config: KeepMajority.Config): Either[Throwable, StrategyDecision] =
      keepMajority(worldView, config).asRight
  }
}
