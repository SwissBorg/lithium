package akka.cluster.sbr.strategies.indirected

import akka.cluster.sbr._
import cats.implicits._

final case object Indirected {
  implicit val indirectedStrategy: Strategy[Indirected.type] = new Strategy[Indirected.type] {
    override def handle(strategy: Indirected.type, worldView: WorldView): Either[Throwable, StrategyDecision] =
      if (worldView.selfStatus === Unreachable) {
        DownSelf(worldView).asRight
      } else {
        Idle.asRight
      }
  }
}
