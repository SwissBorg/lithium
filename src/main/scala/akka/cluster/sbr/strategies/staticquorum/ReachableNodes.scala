package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.WorldView
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._

sealed abstract private[staticquorum] class ReachableNodes extends Product with Serializable

private[staticquorum] object ReachableNodes {
  def apply(worldView: WorldView, quorumSize: Int Refined Positive, role: String): ReachableNodes =
    if (worldView.consideredReachableNodesWithRole(role).size >= quorumSize) {
      ReachableQuorum
    } else {
      ReachableSubQuorum
    }
}

private[staticquorum] case object ReachableQuorum    extends ReachableNodes
private[staticquorum] case object ReachableSubQuorum extends ReachableNodes
