package com.swissborg.lithium.strategy

import com.typesafe.config.Config

/**
 * [[KeepReferee]] config.
 *
 * @param referee                the address of the referee.
 * @param downAllIfLessThanNodes the minimum number of nodes that should be remaining in the cluster.
 *                               Else the cluster gets downed.
 */
final case class KeepRefereeConfig(referee: String, downAllIfLessThanNodes: Int)

object KeepRefereeConfig extends StrategyConfig[KeepRefereeConfig] {
  override val name: String = "keep-referee"

  override def fromConfig(config: Config): KeepRefereeConfig = {
    val prefix = s"com.swissborg.lithium.${name}"

    val referee                = config.getString(s"${prefix}.referee")
    val downAllIfLessThanNodes = config.getInt(s"${prefix}.down-all-if-less-than-nodes")
    if (downAllIfLessThanNodes <= 0) {
      throw new IllegalArgumentException("'down-all-if-less-than-nodes' must be > 0")
    }

    KeepRefereeConfig(referee, downAllIfLessThanNodes)
  }
}
