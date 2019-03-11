package akka.cluster.sbr

import cats.implicits._
import pureconfig.ConfigReader

trait Strategy[A] {
  type Config
  val name: String // TODO refine
  def handle(worldView: WorldView, config: Config): Either[Throwable, StrategyDecision]
}

object Strategy {
  type Aux[A, Config0] = Strategy[A] { type Config = Config0 }

  def apply[A]: PartiallyAppliedStrategy[A] = new PartiallyAppliedStrategy[A]

  implicit class PartiallyAppliedStrategy[A](private val dummy: Boolean = true) extends AnyVal {
    def apply[Config](worldView: WorldView,
                      config: Config)(implicit ev: Aux[A, Config]): Either[Throwable, StrategyDecision] =
      ev.handle(worldView, config)

    def fromConfig[Config: ConfigReader](
      worldView: WorldView
    )(implicit ev: Aux[A, Config]): Either[Throwable, StrategyDecision] =
      pureconfig
        .loadConfig[Config](s"ns.${ev.name}")
        .leftMap(_ => new IllegalArgumentException("yo mama"))
        .flatMap(ev.handle(worldView, _))
  }
}
