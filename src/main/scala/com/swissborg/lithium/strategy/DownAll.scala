package com.swissborg.lithium

package strategy

import cats.Applicative
import cats.implicits._

/**
  * Split-brain resolver strategy that will down all the nodes in the cluster when a node is detected as unreachable.
  */
private[lithium] class DownAll[F[_]: Applicative] extends Strategy[F] {
  override def takeDecision(worldView: WorldView): F[Decision] =
    Decision.downReachable(worldView).pure[F]
}

private[lithium] object DownAll {
  val name: String = "down-all"
}
