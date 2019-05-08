package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.WorldView
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._

sealed abstract private[staticquorum] class UnreachableNodes

private[staticquorum] object UnreachableNodes {
  def apply(worldView: WorldView, quorumSize: Int Refined Positive, role: String): UnreachableNodes = {
    val unreachableNodes = worldView.consideredUnreachableNodesWithRole(role)

    if (unreachableNodes.isEmpty) EmptyUnreachable
    else {
      if (unreachableNodes.size >= quorumSize)
        UnreachablePotentialQuorum
      else
        UnreachableSubQuorum
    }
  }
}

private[staticquorum] case object UnreachablePotentialQuorum extends UnreachableNodes
private[staticquorum] case object UnreachableSubQuorum       extends UnreachableNodes
private[staticquorum] case object EmptyUnreachable           extends UnreachableNodes
