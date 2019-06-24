package com.swissborg.sbr
package strategy

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._

sealed abstract private[sbr] class ReachableQuorum extends Product with Serializable

private[sbr] object ReachableQuorum {
  def apply(worldView: WorldView, quorumSize: Int Refined Positive, role: String): ReachableQuorum =
    if (worldView.nonJoiningReachableNodesWithRole(role).size >= quorumSize) {
      Quorum
    } else {
      NoQuorum
    }

  case object Quorum extends ReachableQuorum
  case object NoQuorum extends ReachableQuorum
}
