package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.WorldView

sealed abstract private[staticquorum] class UnreachableNodes

private[staticquorum] object UnreachableNodes {
  def apply(worldView: WorldView, quorumSize: Int, role: String): UnreachableNodes = {
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
