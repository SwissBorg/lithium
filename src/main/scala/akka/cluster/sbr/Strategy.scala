package akka.cluster.sbr

import pureconfig.ConfigReader
import pureconfig.error.ConfigReaderFailures

trait Strategy[A] {
  type Config
  val name: String
  def handle(worldView: WorldView, config: Config): Either[Throwable, StrategyDecision]
}

object Strategy {
  type Aux[A, Config0] = Strategy[A] { type Config = Config0 }

  def apply[A]: PartiallyAppliedStrategy[A] = new PartiallyAppliedStrategy[A]

  def name[A](implicit ev: Strategy[A]): String = ev.name

  implicit class PartiallyAppliedStrategy[A](private val dummy: Boolean = true) extends AnyVal {
    def apply[Config](worldView: WorldView,
                      config: Config)(implicit ev: Aux[A, Config]): Either[Throwable, StrategyDecision] =
      ev.handle(worldView, config)

    def fromConfig[Config: ConfigReader](
      implicit ev: Aux[A, Config],
    ): Either[ConfigReaderFailures, ConfiguredStrategy[A, Config]] =
      pureconfig
        .loadConfig[Config](s"ns.${ev.name}") // TODO define a namespace
        .map(a => new ConfiguredStrategy[A, Config](a))

    def noConfig(implicit ev: Aux[A, Unit]): ConfiguredStrategy[A, Unit] = new ConfiguredStrategy[A, Unit](())
  }
}
