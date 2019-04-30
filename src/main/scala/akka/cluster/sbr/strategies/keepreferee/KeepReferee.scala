package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.{Strategy, StrategyReader}
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

final case class KeepReferee(address: String, downAllIfLessThanNodes: Int Refined Positive) extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    (KeepRefereeView(worldView, address, downAllIfLessThanNodes) match {
      case RefereeReachable                          => DownUnreachable(worldView)
      case TooFewReachableNodes | RefereeUnreachable => DownReachable(worldView)
    }).asRight
}

object KeepReferee extends StrategyReader[KeepReferee] {
  override val name: String = "keep-referee"
}
