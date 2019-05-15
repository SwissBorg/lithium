package com.swissborg.sbr.strategy

import com.swissborg.sbr.{StrategyDecision, WorldView}

/**
 * Interface for split-brain resolution strategies.
 */
trait Strategy {

  /**
   * Attempts to provide strategy decision given the world view
   * otherwise an error.
   *
   * @param worldView the world view of the cluster from the callers actor sytem.
   */
  def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision]
}
