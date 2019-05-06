package akka.cluster.sbr.strategies.indirectlyconnected

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.Strategy
import cats.implicits._

/**
 * Strategy downing all indirectly connected nodes.
 */
final case class IndirectlyConnected() extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    DownIndirectlyConnected(worldView).asRight
}
