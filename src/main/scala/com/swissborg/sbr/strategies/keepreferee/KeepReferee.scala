package com.swissborg.sbr.strategies.keepreferee

import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategies.keepreferee.KeepReferee.Address
import com.swissborg.sbr.strategy.{Strategy, StrategyReader}
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._
import eu.timepit.refined.string._

final case class KeepReferee(address: String Refined Address, downAllIfLessThanNodes: Int Refined Positive)
    extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    worldView.consideredReachableNodes
      .find(_.member.address.toString === address.value)
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
  type Address = MatchesRegex[W.`"([0-9A-Za-z]+.)*[0-9A-Za-z]+://[0-9A-Za-z]+@([0-9A-Za-z]+.)*[0-9A-Za-z]+:[0-9]+"`.T]
}
