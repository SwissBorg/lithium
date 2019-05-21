package com.swissborg.sbr.strategies

import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr.strategy.Strategy
import com.swissborg.sbr.{StrategyDecision, WorldView}

/**
 * Strategy combining `a` and `b` by taking the union
 * of both decisions.
 */
final case class Union(a: Strategy, b: Strategy) extends Strategy {
  override def takeDecision(worldView: WorldView): SyncIO[StrategyDecision] =
    (a.takeDecision(worldView), b.takeDecision(worldView)).mapN(_ |+| _)
}
