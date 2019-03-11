package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr._
import cats.data.NonEmptySet
import cats.implicits._

final case class KeepMajority()

object KeepMajority {
  def keepMajority(worldView: WorldView): Either[Throwable, StrategyDecision] =
    NodesMajority(worldView) match {
      case ReachableMajority(_) =>
        NonEmptySet.fromSet(worldView.unreachableNodes).fold[StrategyDecision](Idle)(DownUnreachable).asRight

      case UnreachableMajority(_) =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable).asRight

      // Same as Akka
      case NoMajority =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable).asRight
    }

  implicit val keepMajorityStrategy: Strategy.Aux[KeepMajority, Unit] = new Strategy[KeepMajority] {
    override type B = Unit
    override val name: String = "keep-majority"
    override def handle(worldView: WorldView, b: Unit): Either[Throwable, StrategyDecision] = keepMajority(worldView)
  }
}
