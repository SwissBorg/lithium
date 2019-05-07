package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.{Strategy, StrategyReader}
import cats.implicits._

final case class KeepReferee(address: String, downAllIfLessThanNodes: Int) extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    worldView.consideredReachableNodes
      .find(_.member.address.toString === address)
      .fold[StrategyDecision](DownReachable(worldView)) { _ =>
        if (worldView.consideredReachableNodes.size < downAllIfLessThanNodes)
          DownReachable(worldView)
        else
          DownUnreachable(worldView)
      }
      .asRight
}

object KeepReferee extends StrategyReader[KeepReferee] {
  override val name: String = "keep-referee"
}
