package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.Strategy
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

final case class KeepReferee(address: String, downAllIfLessThanNodes: Int Refined Positive)

object KeepReferee {
  def keepReferee(strategy: KeepReferee, worldView: WorldView): StrategyDecision =
    KeepRefereeView(worldView, strategy.address, strategy.downAllIfLessThanNodes) match {
      case RefereeReachable                          => DownUnreachable(worldView)
      case TooFewReachableNodes | RefereeUnreachable => DownReachable(worldView)
    }

  implicit val keepRefereeStrategy: Strategy[KeepReferee] = new Strategy[KeepReferee] {
    override def takeDecision(strategy: KeepReferee, worldView: WorldView): Either[Throwable, StrategyDecision] =
      keepReferee(strategy, worldView).asRight
  }

  implicit val keepRefereeStrategyReader: StrategyReader[KeepReferee] = StrategyReader.fromName("keep-referee")
}
