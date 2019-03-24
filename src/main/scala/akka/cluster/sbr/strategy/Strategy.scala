package akka.cluster.sbr.strategy

import akka.cluster.sbr.{StrategyDecision, WorldView}

trait Strategy[A] {
  def handle(strategy: A, worldView: WorldView): Either[Throwable, StrategyDecision]
}

object Strategy {
  def apply[A](implicit ev: Strategy[A]): Strategy[A] = ev

  implicit class StrategyOps[A: Strategy](private val a: A) {
    def handle(worldView: WorldView): Either[Throwable, StrategyDecision] =
      Strategy[A].handle(a, worldView)
  }
}
