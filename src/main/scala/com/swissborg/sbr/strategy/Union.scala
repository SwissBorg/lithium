package com.swissborg.sbr.strategy

import cats.implicits._
import cats.{Functor, Semigroupal}
import com.swissborg.sbr.WorldView

/**
  * Strategy combining `a` and `b` by taking the union
  * of both decisions.
  */
private[sbr] class Union[F[_]: Functor: Semigroupal](a: Strategy[F], b: Strategy[F])
    extends Strategy[F] {
  override def takeDecision(worldView: WorldView): F[StrategyDecision] =
    (a.takeDecision(worldView), b.takeDecision(worldView)).mapN(_ |+| _)
}
