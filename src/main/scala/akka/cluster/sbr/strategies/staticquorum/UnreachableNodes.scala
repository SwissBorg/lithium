package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.WorldView
import eu.timepit.refined.auto._

sealed abstract private[staticquorum] class UnreachableNodes

private[staticquorum] object UnreachableNodes {
  def apply(worldView: WorldView, quorumSize: QuorumSize, role: String): UnreachableNodes = {
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

final private[staticquorum] case object UnreachablePotentialQuorum extends UnreachableNodes
final private[staticquorum] case object UnreachableSubQuorum       extends UnreachableNodes

final private[staticquorum] case object EmptyUnreachable extends UnreachableNodes
