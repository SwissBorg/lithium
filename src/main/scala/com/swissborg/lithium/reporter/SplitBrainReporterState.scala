package com.swissborg.lithium

package reporter

import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.{Member, MemberStatus, UniqueAddress}
import com.swissborg.lithium.WorldView

/**
  * State of the [[SplitBrainReporter]].
  *
  * @param worldView the view of the cluster from the current cluster node.
  */
final private[reporter] case class SplitBrainReporterState(worldView: WorldView) {

  def updatedMember(m: Member): SplitBrainReporterState =
    m.status match {
      case MemberStatus.Removed => copy(worldView = worldView.removeMember(m))
      case _                    => copy(worldView = worldView.addOrUpdate(m))
    }

  /**
    * Set the node as reachable.
    */
  def withReachableNode(node: UniqueAddress): SplitBrainReporterState =
    copy(worldView = worldView.withReachableNode(node))

  /**
    * Set the node as unreachable.
    */
  def withUnreachableNode(node: UniqueAddress): SplitBrainReporterState =
    copy(worldView = worldView.withUnreachableNode(node))

  /**
    * Set the node as indirectly connected.
    */
  def withIndirectlyConnectedNode(node: UniqueAddress): SplitBrainReporterState =
    copy(worldView = worldView.withIndirectlyConnectedNode(node))
}

private[reporter] object SplitBrainReporterState {

  def fromSnapshot(selfMember: Member, snapshot: CurrentClusterState): SplitBrainReporterState =
    SplitBrainReporterState(WorldView.fromSnapshot(selfMember, snapshot))
}
