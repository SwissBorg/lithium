package com.swissborg.lithium

package reachability

import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterSettings.DataCenter
import akka.cluster.swissborg.LithiumReachability
import akka.cluster.{Member, UniqueAddress}
import cats.data.State
import cats.implicits._
import com.swissborg.lithium.reporter.SplitBrainReporter._

import scala.collection.immutable.SortedSet

/**
  * State of the SBRFailureDetector.
  */
final private[reachability] case class ReachabilityReporterState private (
    selfDataCenter: DataCenter,
    otherDcMembers: SortedSet[UniqueAddress],
    latestReachability: Option[LithiumReachability],
    latestIndirectlyConnectedNodes: Set[UniqueAddress],
    latestUnreachableNodes: Set[UniqueAddress],
    latestReachableNodes: Set[UniqueAddress]
) {
  private[reachability] def addOtherDcMember(member: Member): ReachabilityReporterState =
    if (member.dataCenter =!= selfDataCenter) {
      copy(otherDcMembers = otherDcMembers + member.uniqueAddress)
    } else {
      this
    }

  /**
    * Remove the node.
    */
  private[reachability] def remove(node: UniqueAddress): ReachabilityReporterState =
    copy(
      otherDcMembers = otherDcMembers - node, // no need to check DC
      latestIndirectlyConnectedNodes = latestIndirectlyConnectedNodes - node,
      latestUnreachableNodes = latestUnreachableNodes - node,
      latestReachableNodes = latestReachableNodes - node
    )

  private[reachability] def withReachability(reachability: LithiumReachability): ReachabilityReporterState =
    copy(latestReachability = Some(reachability))
}

private[reachability] object ReachabilityReporterState {

  def apply(selfDataCenter: DataCenter): ReachabilityReporterState =
    ReachabilityReporterState(selfDataCenter, SortedSet.empty, None, Set.empty, Set.empty, Set.empty)

  def fromSnapshot(
      snapshot: CurrentClusterState,
      selfDataCenter: DataCenter
  ): ReachabilityReporterState =
    snapshot.members.foldLeft(ReachabilityReporterState(selfDataCenter))(_.addOtherDcMember(_))

  def updateReachabilities(
      snapshot: CurrentClusterState
  ): State[ReachabilityReporterState, List[NodeReachabilityEvent]] =
    State { s =>
      s.latestReachability.fold((s, List.empty[NodeReachabilityEvent])) { reachability =>
        // Only keep reachability information made by members and of members
        // in this data-center.
        val reachabilityNoOutsideNodes = reachability.remove(s.otherDcMembers)

        // Nodes that have seen the current snapshot and were detected as unreachable.
        val suspiciousUnreachableNodes =
          reachabilityNoOutsideNodes.allUnreachable.filter(node => snapshot.seenBy(node.address))

        val suspiciousObservers = suspiciousUnreachableNodes.foldLeft(Set.empty[UniqueAddress]) {
          case (suspiciousObservers, node) =>
            reachabilityNoOutsideNodes.observersGroupedByUnreachable
              .get(node)
              .fold(suspiciousObservers)(_ ++ suspiciousObservers)
        }

        val indirectlyConnectedNodes = suspiciousUnreachableNodes ++ suspiciousObservers

        val unreachableNodes = reachabilityNoOutsideNodes.allUnreachable -- indirectlyConnectedNodes

        val reachableNodes = snapshot.members
          .collect { case m if reachabilityNoOutsideNodes.isReachable(m.uniqueAddress) => m.uniqueAddress }
          .diff(indirectlyConnectedNodes)

        val newIndirectlyConnectedNodes = indirectlyConnectedNodes -- s.latestIndirectlyConnectedNodes
        val newUnreachableNodes         = unreachableNodes -- s.latestUnreachableNodes
        val newReachableNodes           = reachableNodes -- s.latestReachableNodes

        val updatedReachabilityEvents =
          newIndirectlyConnectedNodes.toList.map(NodeIndirectlyConnected) ++
          newUnreachableNodes.toList.map(NodeUnreachable) ++
          newReachableNodes.toList.map(NodeReachable)

        val updatedState = s.copy(
          latestIndirectlyConnectedNodes = indirectlyConnectedNodes,
          latestUnreachableNodes = unreachableNodes,
          latestReachableNodes = reachableNodes
        )

        (updatedState, updatedReachabilityEvents)
      }
    }
}
