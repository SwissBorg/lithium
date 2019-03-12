package akka.cluster.sbr.strategies.downall

import akka.cluster.sbr._
import cats.data.NonEmptySet
import cats.implicits._

final case class DownAll()

object DownAll {
  def downAll(worldView: WorldView): StrategyDecision =
    if (worldView.unreachableNodes.nonEmpty)
      NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable)
    else Idle

  implicit val downAllStrategy: Strategy.Aux[DownAll, Unit] = new Strategy[DownAll] {
    override type Config = Unit
    override val name: String = "down-all"
    override def handle(worldView: WorldView, config: Config): Either[Throwable, StrategyDecision] =
      downAll(worldView).asRight
  }
}
