package com.swissborg.lithium

package strategy

/**
 * Interface for split-brain resolution strategies.
 */
private[lithium] trait Strategy[F[_]] {

  /**
   * The strategy decision given the world view.
   *
   * @param worldView the view of the cluster from this member.
   */
  def takeDecision(worldView: WorldView): F[Decision]
}
