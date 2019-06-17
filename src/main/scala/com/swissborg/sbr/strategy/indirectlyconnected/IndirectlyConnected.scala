package com.swissborg.sbr.strategy.indirectlyconnected

import cats.Applicative
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.StrategyDecision._
import com.swissborg.sbr.strategy.{Strategy, StrategyDecision}

/**
  * Split-brain resolver strategy that will down all indirectly connected nodes.
  *
  * Indirectly connected nodes are nodes that can only communicate with a subset
  * of all the nodes in the cluster.
  */
private[sbr] class IndirectlyConnected[F[_]: Applicative] extends Strategy[F] {
  override def takeDecision(worldView: WorldView): F[StrategyDecision] =
    downIndirectlyConnected(worldView).pure[F]
}
