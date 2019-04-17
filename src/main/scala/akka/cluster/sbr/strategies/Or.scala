package akka.cluster.sbr.strategies

import akka.cluster.sbr.strategy.Strategy
import akka.cluster.sbr.strategy.ops._
import akka.cluster.sbr.{StrategyDecision, WorldView}
import cats.implicits._

final case class Or[A, B](a: A, b: B)

object Or {
  implicit def orStrategy[A: Strategy, B: Strategy]: Strategy[Or[A, B]] = new Strategy[Or[A, B]] {
    override def takeDecision(strategy: Or[A, B], worldView: WorldView): Either[Throwable, StrategyDecision] =
      (strategy.a.takeDecision(worldView), strategy.b.takeDecision(worldView)).mapN(_ |+| _)
  }
}
