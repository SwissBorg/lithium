package com.swissborg.sbr.strategy

import cats.implicits._
import pureconfig.error.ConfigReaderFailures
import pureconfig.{ConfigReader, Derivation}

/**
 * Interface for loading split-brain resolution strategies from the configuration.
 *
 * @tparam A the type of the strategy to load.
 */
trait StrategyReader[A] {
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
  def load(implicit R: Derivation[ConfigReader[A]]): Either[ConfigReaderError, A] =
    pureconfig
      .loadConfig[A](s"com.swissborg.sbr.$name")
      .leftMap(ConfigReaderError)
}

object StrategyReader {
  sealed abstract class StrategyError(message: String)        extends Throwable(message)
  final case class ConfigReaderError(f: ConfigReaderFailures) extends StrategyError(s"$f")
  final case class UnknownStrategy(strategy: String)          extends StrategyError(strategy)
}
