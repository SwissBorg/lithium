package akka.cluster.sbr.strategies.indirected

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.Strategy
import cats.implicits._

final case object Indirected {
  implicit val indirectedStrategy: Strategy[Indirected.type] = new Strategy[Indirected.type] {
    override def handle(strategy: Indirected.type, worldView: WorldView): Either[Throwable, StrategyDecision] = {
      val decision =
        if (worldView.selfStatus === Unreachable) {
          DownSelf(worldView)
        } else {
          Idle
        }

      decision.asRight
    }
  }
}
