package akka.cluster.sbr.strategies.downall

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.Strategy
import cats.implicits._

/**
 * Strategy that will down all the nodes in the cluster when a node is detected as unreachable.
 *
 * This strategy is useful when the cluster is unstable. todo add more info
 */
final case object DownAll {
  implicit val downAllStrategy: Strategy[DownAll.type] = new Strategy[DownAll.type] {
    override def takeDecision(strategy: DownAll.type, worldView: WorldView): Either[Throwable, StrategyDecision] =
      // When self is indirectly connected it is not reachable.
      DownThese(DownSelf(worldView), DownReachable(worldView)).asRight
  }

  implicit val downAllStrategyReader: StrategyReader[DownAll.type] = StrategyReader.fromName("down-all")
}
