package com.swissborg.sbr.strategies.downall

import cats.Applicative
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.Strategy

/**
 * Split-brain resolver strategy that will down all the nodes in the cluster when a node is detected as unreachable.
 */
class DownAll[F[_]: Applicative] extends Strategy[F] {
  override def takeDecision(worldView: WorldView): F[StrategyDecision] =
    // When self is indirectly connected it is not reachable.
    DownThese(DownSelf(worldView), DownReachable(worldView)).pure[F].widen
}

object DownAll {
  val name: String = "down-all"
}
