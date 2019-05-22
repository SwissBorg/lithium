package com.swissborg.sbr.strategies.indirectlyconnected

import cats.Applicative
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.Strategy

/**
 * Split-brain resolver strategy that will down all indirectly connected nodes.
 *
 * Indirectly connected nodes are nodes that can only communicate with a subset
 * of all the nodes in the cluster.
 */
final case class IndirectlyConnected[F[_]: Applicative]() extends Strategy[F] {
  override def takeDecision(worldView: WorldView): F[StrategyDecision] =
    DownIndirectlyConnected(worldView).pure[F].widen
}
