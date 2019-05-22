package com.swissborg.sbr.strategies

import cats.{Functor, Semigroupal}
import cats.implicits._
import com.swissborg.sbr.strategy.Strategy
import com.swissborg.sbr.{StrategyDecision, WorldView}

/**
 * Strategy combining `a` and `b` by taking the union
 * of both decisions.
 */
class Union[F[_]: Functor: Semigroupal](a: Strategy[F], b: Strategy[F]) extends Strategy[F] {
  override def takeDecision(worldView: WorldView): F[StrategyDecision] =
    (a.takeDecision(worldView), b.takeDecision(worldView)).mapN(_ |+| _)
}
