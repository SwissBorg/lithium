package akka.cluster.sbr.strategy

import akka.cluster.sbr.{StrategyDecision, WorldView}

object ops {
  implicit class StrategyOps[A: Strategy](private val a: A) {
    def handle(worldView: WorldView): Either[Throwable, StrategyDecision] =
      Strategy[A].handle(a, worldView)
  }
}
