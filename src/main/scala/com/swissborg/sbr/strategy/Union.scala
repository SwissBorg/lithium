package com.swissborg.sbr.strategy

import cats.implicits._
import cats.{Functor, Semigroupal}
import com.swissborg.sbr.WorldView

/**
  * Strategy combining `a` and `b` by taking the union
  * of both decisions.
  */
private[sbr] class Union[F[_]: Functor: Semigroupal, Strat1[_[_]], Strat2[_[_]]](
    a: Strat1[F],
    b: Strat2[F]
)(implicit ev1: Strat1[F] <:< Strategy[F], ev2: Strat2[F] <:< Strategy[F])
    extends Strategy[F] {
  override def takeDecision(worldView: WorldView): F[StrategyDecision] =
    (a.takeDecision(worldView), b.takeDecision(worldView)).mapN(_ |+| _)
}
