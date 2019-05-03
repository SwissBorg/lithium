package akka.cluster.sbr.strategies.downall

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.{Strategy, StrategyReader}
import cats.implicits._

/**
 * Strategy that will down all the nodes in the cluster when a node is detected as unreachable.
 *
 * This strategy is useful when the cluster is unstable. todo add more info
 */
final case class DownAll() extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    // When self is indirectly connected it is not reachable.
    DownThese(DownSelf(worldView), DownReachable(worldView)).asRight
}

object DownAll extends StrategyReader[DownAll] {
  override val name: String = "down-all"
}
