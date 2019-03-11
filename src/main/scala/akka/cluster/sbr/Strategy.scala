package akka.cluster.sbr

import cats.implicits._
import pureconfig.ConfigReader

trait Strategy[A] {
  type B
  val name: String // TODO refine
  def handle(worldView: WorldView, b: B): Either[Throwable, StrategyDecision]
}

object Strategy {
  type Aux[A, B0] = Strategy[A] { type B = B0 }

  def apply[A]: PartiallyAppliedStrategy[A] = new PartiallyAppliedStrategy[A]

  implicit class PartiallyAppliedStrategy[A](private val dummy: Boolean = true) extends AnyVal {
    def apply[B](worldView: WorldView, b: B)(implicit ev: Aux[A, B]): Either[Throwable, StrategyDecision] =
      ev.handle(worldView, b)

    def fromConfig[B: ConfigReader](worldView: WorldView)(implicit ev: Aux[A, B]): Either[Throwable, StrategyDecision] =
      pureconfig
        .loadConfig[B](s"ns.${ev.name}")
        .leftMap(_ => new IllegalArgumentException("yo mama"))
        .flatMap(ev.handle(worldView, _))
  }
}
