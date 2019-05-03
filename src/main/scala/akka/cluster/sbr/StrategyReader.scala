package akka.cluster.sbr

import cats.implicits._
import pureconfig.error.ConfigReaderFailures
import pureconfig.{ConfigReader, Derivation}

trait StrategyReader[A] {
  import StrategyReader._

  val name: String
  def load(implicit R: Derivation[ConfigReader[A]]): Either[ConfigReaderError, A] =
    pureconfig
      .loadConfig[A](s"akka.cluster.split-brain-resolver.$name")
      .leftMap(ConfigReaderError)
}

object StrategyReader {
  def apply[A](implicit ev: StrategyReader[A]): StrategyReader[A] = ev

  def fromName[A](name0: String): StrategyReader[A] = new StrategyReader[A] {
    override val name: String = name0
  }

  sealed abstract class StrategyError(message: String)        extends Throwable(message)
  final case class ConfigReaderError(f: ConfigReaderFailures) extends StrategyError(s"$f")
  final case class UnknownStrategy(strategy: String)          extends StrategyError(strategy)
}
