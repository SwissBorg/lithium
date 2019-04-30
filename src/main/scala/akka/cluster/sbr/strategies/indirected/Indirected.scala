package akka.cluster.sbr.strategies.indirected

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.{Strategy, StrategyReader}
import cats.implicits._

final case class Indirected() extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    DownIndirectlyConnected(worldView).asRight
}

object Indirected extends StrategyReader[Indirected] {
  override val name: String = "indirected"
}
