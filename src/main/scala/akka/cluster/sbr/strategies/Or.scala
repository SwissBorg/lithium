package akka.cluster.sbr.strategies

import akka.cluster.sbr.strategy.Strategy
import akka.cluster.sbr.{StrategyDecision, WorldView}
import cats.implicits._

final case class Or(a: Strategy, b: Strategy) extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    (a.takeDecision(worldView), b.takeDecision(worldView)).mapN(_ |+| _)
}
