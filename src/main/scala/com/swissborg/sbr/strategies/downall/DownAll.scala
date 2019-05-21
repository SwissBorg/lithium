package com.swissborg.sbr.strategies.downall

import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.{Strategy, StrategyReader}

/**
 * Strategy that will down all the nodes in the cluster when a node is detected as unreachable.
 *
 * This strategy is useful when the cluster is unstable. todo add more info
 */
final case class DownAll() extends Strategy {
  override def takeDecision(worldView: WorldView): SyncIO[StrategyDecision] =
    // When self is indirectly connected it is not reachable.
    DownThese(DownSelf(worldView), DownReachable(worldView)).pure[SyncIO]
}

object DownAll extends StrategyReader[DownAll] {
  override val name: String = "down-all"
}
