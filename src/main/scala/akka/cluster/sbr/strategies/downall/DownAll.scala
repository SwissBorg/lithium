package akka.cluster.sbr.strategies.downall

import akka.cluster.sbr._
import cats.implicits._

/**
 * Strategy that will down all the nodes in the cluster when a node is detected as unreachable.
 *
 * This strategy is useful when the cluster is unstable. todo add more info
 */
final case object DownAll {
  def downAll(worldView: WorldView): StrategyDecision = DownReachable(worldView)

  implicit val downAllStrategy: Strategy[DownAll.type] = new Strategy[DownAll.type] {
    override def handle(strategy: DownAll.type, worldView: WorldView): Either[Throwable, StrategyDecision] =
      downAll(worldView).asRight
  }

  implicit val downAllStrategyReader: StrategyReader[DownAll.type] = StrategyReader.fromName("down-all")
}
