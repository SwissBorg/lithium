package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.WorldView
import eu.timepit.refined.auto._

sealed abstract private[staticquorum] class ReachableNodes extends Product with Serializable

private[staticquorum] object ReachableNodes {
  def apply(worldView: WorldView, quorumSize: QuorumSize, role: String): ReachableNodes =
    if (worldView.consideredReachableNodesWithRole(role).size >= quorumSize) {
      ReachableQuorum
    } else {
      ReachableSubQuorum
    }
}

final private[staticquorum] case object ReachableQuorum    extends ReachableNodes
final private[staticquorum] case object ReachableSubQuorum extends ReachableNodes
