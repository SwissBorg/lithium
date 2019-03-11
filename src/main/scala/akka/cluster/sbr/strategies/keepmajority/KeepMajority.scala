package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr._
import cats.data.NonEmptySet
import cats.implicits._

final case class KeepMajority()

object KeepMajority {
  def keepMajority(worldView: WorldView): StrategyDecision =
    NodesMajority(worldView) match {
      case ReachableMajority(_) =>
        NonEmptySet.fromSet(worldView.unreachableNodes).fold[StrategyDecision](Idle)(DownUnreachable)

      case UnreachableMajority(_) =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable)

      // Same as Akka
      case NoMajority =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable)
    }

  implicit val keepMajorityStrategy: Strategy.Aux[KeepMajority, Unit] = new Strategy[KeepMajority] {
    override type Config = Unit
    override val name: String = "keep-majority"
    override def handle(worldView: WorldView, config: Unit): Either[Throwable, StrategyDecision] =
      keepMajority(worldView).asRight
  }
}
