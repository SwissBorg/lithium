package com.swissborg.lithium

package reachability

import akka.actor.Address
import akka.cluster.ClusterSettings.DataCenter
import akka.cluster.swissborg.LithiumReachability
import akka.cluster.swissborg.implicits._
import akka.cluster.{Member, UniqueAddress}
import cats.data.State
import cats.implicits._
import com.swissborg.lithium.reachability.ReachabilityReporterState.LatestReceived
import com.swissborg.lithium.reporter.SplitBrainReporter._

import scala.collection.immutable.SortedSet

/**
 * State of the `ReachabilityReporter`.
 */
final private[reachability] case class ReachabilityReporterState private (
  selfDataCenter: DataCenter,
  members: SortedSet[Member],
  otherDcMembers: Set[UniqueAddress],
  latestReachability: Option[LithiumReachability],
  latestSeenBy: Option[Set[Address]],
  latestReceived: Option[LatestReceived],
  latestIndirectlyConnectedNodes: Set[UniqueAddress],
  latestUnreachableNodes: Set[UniqueAddress],
  latestReachableNodes: Set[UniqueAddress]
) {
  private[reachability] def withMembers(members: Set[Member]): ReachabilityReporterState = {
    // todo make this quicker
    val removed = (members.map(_.uniqueAddress) ++ otherDcMembers) -- members.map(_.uniqueAddress)
    copy(
      members = SortedSet(members.filter(_.dataCenter === selfDataCenter).toSeq: _*),
      otherDcMembers = otherDcMembers -- removed,
      latestIndirectlyConnectedNodes = latestIndirectlyConnectedNodes -- removed,
      latestUnreachableNodes = latestUnreachableNodes -- removed,
      latestReachableNodes = latestReachableNodes -- removed
    )
  }

  private[reachability] def withMember(member: Member): ReachabilityReporterState =
    if (member.dataCenter === selfDataCenter) {
      copy(members = members + member)
    } else {
      copy(otherDcMembers = otherDcMembers + member.uniqueAddress)
    }

  /**
   * Remove the node.
   */
  private[reachability] def remove(node: UniqueAddress): ReachabilityReporterState =
    copy(
      members = members.filterNot(m => m.uniqueAddress === node),
      otherDcMembers = otherDcMembers - node, // no need to check DC
      latestIndirectlyConnectedNodes = latestIndirectlyConnectedNodes - node,
      latestUnreachableNodes = latestUnreachableNodes - node,
      latestReachableNodes = latestReachableNodes - node
    )
}

private[reachability] object ReachabilityReporterState {

  sealed abstract class LatestReceived

  object LatestReceived {

    case object SeenBy extends LatestReceived

    case object Reachability extends LatestReceived

  }

  def apply(selfDataCenter: DataCenter): ReachabilityReporterState =
    ReachabilityReporterState(selfDataCenter,
                              SortedSet.empty,
                              SortedSet.empty,
                              None,
                              None,
                              None,
                              Set.empty,
                              Set.empty,
                              Set.empty)
  def withSeenBy(seenBy: Set[Address]): State[ReachabilityReporterState, List[NodeReachabilityEvent]] =
    State
      .get[ReachabilityReporterState]
      .flatMap(
        s =>
          s.latestReceived.fold(ignore) {
            case LatestReceived.SeenBy =>
              s.latestReachability.fold(ignore)(updatedReachabilityEvents(_, seenBy, s.members))
            case LatestReceived.Reachability => ignore
          }
      )
      .modify(_.copy(latestSeenBy = Some(seenBy), latestReceived = Some(LatestReceived.SeenBy)))

  def withReachability(
    reachability: LithiumReachability
  ): State[ReachabilityReporterState, List[NodeReachabilityEvent]] =
    State
      .get[ReachabilityReporterState]
      .flatMap(
        s =>
          s.latestReceived
            .fold(ignore)(_ => s.latestSeenBy.fold(ignore)(updatedReachabilityEvents(reachability, _, s.members)))
      )
      .modify(_.copy(latestReachability = Some(reachability), latestReceived = Some(LatestReceived.Reachability)))

  private val ignore = State.empty[ReachabilityReporterState, List[NodeReachabilityEvent]]

  private def updatedReachabilityEvents(
    reachability: LithiumReachability,
    seenBy: Set[Address],
    members: SortedSet[Member]
  ): State[ReachabilityReporterState, List[NodeReachabilityEvent]] = State { s =>
    // Only keep reachability information made by members and of members
    // in this data-center.
    val reachabilityNoOutsideNodes = reachability.remove(s.otherDcMembers)

    // Nodes that have seen the current snapshot and were detected as unreachable.
    val suspiciousUnreachableNodes =
      reachabilityNoOutsideNodes.allUnreachable.filter(node => seenBy(node.address))

    val suspiciousObservers = suspiciousUnreachableNodes.foldLeft(Set.empty[UniqueAddress]) {
      case (suspiciousObservers, node) =>
        reachabilityNoOutsideNodes.observersGroupedByUnreachable
          .get(node)
          .fold(suspiciousObservers)(_ ++ suspiciousObservers)
    }

    val indirectlyConnectedNodes = suspiciousUnreachableNodes ++ suspiciousObservers

    val unreachableNodes = reachabilityNoOutsideNodes.allUnreachable -- indirectlyConnectedNodes

    val reachableNodes = members
      .collect {
        case m if m.dataCenter === s.selfDataCenter && reachabilityNoOutsideNodes.isReachable(m.uniqueAddress) =>
          m.uniqueAddress
      }
      .diff(indirectlyConnectedNodes)

    val newIndirectlyConnectedNodes = indirectlyConnectedNodes -- s.latestIndirectlyConnectedNodes
    val newUnreachableNodes         = unreachableNodes -- s.latestUnreachableNodes
    val newReachableNodes           = reachableNodes -- s.latestReachableNodes

    val updatedReachabilityEvents =
      newIndirectlyConnectedNodes.toList.map(NodeIndirectlyConnected) ++
        newUnreachableNodes.toList.map(NodeUnreachable) ++
        newReachableNodes.toList.map(NodeReachable)

    val updatedState = s.copy(latestIndirectlyConnectedNodes = indirectlyConnectedNodes,
                              latestUnreachableNodes = unreachableNodes,
                              latestReachableNodes = reachableNodes)

    (updatedState, updatedReachabilityEvents)
  }
}
