package com.swissborg.sbr.strategy.keepreferee

import cats.Applicative
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.keepreferee.KeepReferee.Config
import com.swissborg.sbr.strategy.keepreferee.KeepReferee.Config.Address
import com.swissborg.sbr.strategy.{Strategy, StrategyDecision, StrategyReader}
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._
import eu.timepit.refined.string._

/**
  * Split-brain strategy that will keep the partition containing the referee (`config.address`)
  * and down all the other partitions.
  * If the remaining partition has less than `config.downAllIfLessThanNodes` the cluster will
  * be downed.
  *
  * This strategy is useful when the cluster has a node that is critical to its operation.
  */
class KeepReferee[F[_]: Applicative](config: Config) extends Strategy[F] {
  import config._

  override def takeDecision(worldView: WorldView): F[StrategyDecision] =
    worldView.consideredReachableNodes
      .find(_.member.address.toString === address.value)
      .fold(StrategyDecision.downReachable(worldView)) { _ =>
        if (worldView.consideredReachableNodes.size < downAllIfLessThanNodes)
          StrategyDecision.downReachable(worldView)
        else
          StrategyDecision.downUnreachable(worldView)
      }
      .pure[F]
}

object KeepReferee {

  /**
    * [[KeepReferee]] config.
    *
    * @param address the address of the referee.
    * @param downAllIfLessThanNodes the minimum number of nodes that should be remaining in the cluster.
    *                                 Else the cluster gets downed.
    */
  final case class Config(
      address: String Refined Address,
      downAllIfLessThanNodes: Int Refined Positive
  )

  object Config extends StrategyReader[Config] {
    override val name: String = "keep-referee"
    type Address = MatchesRegex[
      W.`"([0-9A-Za-z]+.)*[0-9A-Za-z]+://[0-9A-Za-z]+@([0-9A-Za-z]+.)*[0-9A-Za-z]+:[0-9]+"`.T
    ]
  }
}
