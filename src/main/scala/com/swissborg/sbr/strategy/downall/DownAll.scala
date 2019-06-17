package com.swissborg.sbr.strategy.downall

import cats.Applicative
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.StrategyDecision._
import com.swissborg.sbr.strategy.{Strategy, StrategyDecision}

/**
  * Split-brain resolver strategy that will down all the nodes in the cluster when a node is detected as unreachable.
  */
private[sbr] class DownAll[F[_]: Applicative] extends Strategy[F] {
  override def takeDecision(worldView: WorldView): F[StrategyDecision] =
    downReachable(worldView).pure[F]
}

private[sbr] object DownAll {
  val name: String = "down-all"
}
