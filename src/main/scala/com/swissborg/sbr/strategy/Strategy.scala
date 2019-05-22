package com.swissborg.sbr.strategy

import com.swissborg.sbr.{StrategyDecision, WorldView}

/**
 * Interface for split-brain resolution strategies.
 */
trait Strategy[F[_]] {

  /**
   * The strategy decision given the world view.
   *
   * @param worldView the world view of the cluster from the callers actor sytem.
   */
  def takeDecision(worldView: WorldView): F[StrategyDecision]
}
