package com.swissborg.sbr.strategies.indirectlyconnected

import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.Strategy

/**
 * Strategy downing all indirectly connected nodes.
 */
final case class IndirectlyConnected() extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    DownIndirectlyConnected(worldView).asRight
}
