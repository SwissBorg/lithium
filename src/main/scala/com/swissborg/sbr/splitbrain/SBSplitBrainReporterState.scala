package com.swissborg.sbr.splitbrain

import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.{Member, MemberStatus, UniqueAddress}
import com.swissborg.sbr.WorldView

/**
  * State of the [[SBSplitBrainReporter]].
  *
  * @param worldView the view of the cluster from the current cluster node.
  */
final case class SBSplitBrainReporterState(worldView: WorldView) {
  def updatedMember(m: Member): SBSplitBrainReporterState =
    m.status match {
//      case MemberStatus.Down    => copy(worldView = worldView.removeMember(m))
      case MemberStatus.Removed => copy(worldView = worldView.removeMember(m))
      case _                    => copy(worldView = worldView.addOrUpdate(m))

    }

  /**
    * Set the node as reachable.
    */
  def withReachableNode(node: UniqueAddress): SBSplitBrainReporterState =
    copy(worldView = worldView.withReachableNode(node))

  /**
    * Set the node as unreachable.
    */
  def withUnreachableNode(node: UniqueAddress): SBSplitBrainReporterState =
    copy(worldView = worldView.withUnreachableNode(node))

  /**
    * Set the node as indirectly connected.
    */
  def withIndirectlyConnectedNode(node: UniqueAddress): SBSplitBrainReporterState =
    copy(worldView = worldView.withIndirectlyConnectedNode(node))
}

object SBSplitBrainReporterState {
  def fromSnapshot(selfMember: Member, snapshot: CurrentClusterState): SBSplitBrainReporterState =
    SBSplitBrainReporterState(WorldView.fromSnapshot(selfMember, snapshot))
}
