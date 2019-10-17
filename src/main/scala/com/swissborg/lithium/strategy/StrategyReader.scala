package com.swissborg.lithium

package strategy

import cats.implicits._
import com.typesafe.config.Config
import pureconfig.error.ConfigReaderFailures
import pureconfig.{ConfigReader, ConfigSource, Derivation}

/**
 * Interface for loading split-brain resolution strategies from the configuration.
 *
 * @tparam A the type of the strategy to load.
 */
private[lithium] trait StrategyReader[A] {

  import StrategyReader._

  /**
   * The name of the strategy that will have to configured at
   * the path `akka.cluster.split-brain-resolver.insert-strategy-name-here` to be
   * load `A`.
   */
  def name: String

  /**
   * Attempts to load the strategy `A` otherwise an error.
   */
  def load(config: Config)(implicit R: Derivation[ConfigReader[A]]): Either[ConfigReaderError, A] =
    ConfigSource.fromConfig(config).at(s"com.swissborg.lithium.$name").load[A].leftMap(ConfigReaderError)
}

private[lithium] object StrategyReader {

  sealed abstract class StrategyError(message: String) extends Throwable(message) with Product with Serializable

  final case class ConfigReaderError(f: ConfigReaderFailures) extends StrategyError(s"$f")

  final case class UnknownStrategy(strategy: String) extends StrategyError(strategy)

}
