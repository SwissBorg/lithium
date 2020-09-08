package com.swissborg.lithium

package strategy

import cats.Applicative
import cats.syntax.all._

/**
 * Split-brain resolver strategy that will down all indirectly connected nodes.
 *
 * Indirectly connected nodes are nodes that can only communicate with a subset
 * of all the nodes in the cluster.
 */
private[lithium] class IndirectlyConnected[F[_]: Applicative] extends Strategy[F] {
  override def takeDecision(worldView: WorldView): F[Decision] =
    Decision.downIndirectlyConnected(worldView).pure[F]
}
