package com.swissborg.lithium

package strategy

import com.typesafe.config.Config

/**
 * Interface for loading split-brain resolution strategies from the configuration.
 *
 * @tparam A the type of the strategy to load.
 */
private[lithium] trait StrategyConfig[A] {

  /**
   * The name of the strategy that will have to configured at
   * the path `akka.cluster.split-brain-resolver.insert-strategy-name-here` to be
   * load `A`.
   */
  def name: String

  /**
   * Attempts to load the strategy `A` otherwise an error.
   */
  def fromConfig(config: Config): A
}
