package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.WorldView

sealed abstract private[staticquorum] class ReachableNodes extends Product with Serializable

private[staticquorum] object ReachableNodes {
  def apply(worldView: WorldView, quorumSize: Int, role: String): ReachableNodes =
    if (worldView.consideredReachableNodesWithRole(role).size >= quorumSize) {
      ReachableQuorum
    } else {
      ReachableSubQuorum
    }
}

private[staticquorum] case object ReachableQuorum    extends ReachableNodes
private[staticquorum] case object ReachableSubQuorum extends ReachableNodes
