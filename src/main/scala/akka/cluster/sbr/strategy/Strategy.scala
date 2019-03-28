package akka.cluster.sbr.strategy

import akka.cluster.sbr.{StrategyDecision, WorldView}

trait Strategy[A] {
  def takeDecision(strategy: A, worldView: WorldView): Either[Throwable, StrategyDecision]
}

object Strategy {
  def apply[A](implicit ev: Strategy[A]): Strategy[A] = ev
}
