package com.swissborg.sbr.strategy.staticquorum

import com.swissborg.sbr.WorldView
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._

sealed abstract private[staticquorum] class ReachableQuorum extends Product with Serializable

private[staticquorum] object ReachableQuorum {
  def apply(worldView: WorldView, quorumSize: Int Refined Positive, role: String): ReachableQuorum =
    if (worldView.nonJoiningReachableNodesWithRole(role).size >= quorumSize) {
      Quorum
    } else {
      NoQuorum
    }

  case object Quorum extends ReachableQuorum
  case object NoQuorum extends ReachableQuorum
}
