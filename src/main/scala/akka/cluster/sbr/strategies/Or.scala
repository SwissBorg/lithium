package akka.cluster.sbr.strategies

import akka.cluster.sbr.strategy.Strategy
import akka.cluster.sbr.strategy.ops._
import akka.cluster.sbr.{StrategyDecision, WorldView}
import cats.implicits._

final case class Or[A, B](a: A, b: B)

object Or {
  implicit def orStrategy[A: Strategy, B: Strategy]: Strategy[Or[A, B]] = new Strategy[Or[A, B]] {
    override def handle(strategy: Or[A, B], worldView: WorldView): Either[Throwable, StrategyDecision] =
      (strategy.a.handle(worldView), strategy.b.handle(worldView)).mapN(_ |+| _)
  }
}
