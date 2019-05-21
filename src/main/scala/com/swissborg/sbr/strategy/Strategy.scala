package com.swissborg.sbr.strategy

import cats.effect.SyncIO
import com.swissborg.sbr.{StrategyDecision, WorldView}

/**
 * Interface for split-brain resolution strategies.
 */
trait Strategy {

  /**
   * The strategy decision given the world view.
   *
   * @param worldView the world view of the cluster from the callers actor sytem.
   */
  def takeDecision(worldView: WorldView): SyncIO[StrategyDecision]
}
