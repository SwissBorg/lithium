package com.swissborg.sbr.strategy.staticquorum

import com.swissborg.sbr.WorldView
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._

sealed abstract private[staticquorum] class UnreachableQuorum

private[staticquorum] object UnreachableQuorum {
  def apply(
      worldView: WorldView,
      quorumSize: Int Refined Positive,
      role: String
  ): UnreachableQuorum = {
    val unreachableNodes = worldView.nonJoiningUnreachableNodesWithRole(role)

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
