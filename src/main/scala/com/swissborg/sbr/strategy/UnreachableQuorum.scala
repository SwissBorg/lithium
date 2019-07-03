package com.swissborg.sbr
package strategy

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._

sealed abstract private[sbr] class UnreachableQuorum

private[sbr] object UnreachableQuorum {
  def apply(
      worldView: WorldView,
      quorumSize: Int Refined Positive,
      role: String
  ): UnreachableQuorum = {
    val unreachableNodes = worldView.consideredUnreachableNodesWithRole(role)

    if (unreachableNodes.isEmpty) None
    else {
      if (unreachableNodes.size >= quorumSize)
        PotentialQuorum
      else
        SubQuorum
    }
  }

  case object PotentialQuorum extends UnreachableQuorum
  case object SubQuorum extends UnreachableQuorum
  case object None extends UnreachableQuorum
}
