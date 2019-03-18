package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.Member._
import akka.cluster.MemberStatus.WeaklyUp

import scala.collection.immutable.{SortedMap, SortedSet}

/**
 * The cluster from the point of view of a node.
 */
final case class WorldView private[sbr] (private[sbr] val reachabilities: SortedMap[Member, Reachability]) {

  /**
   * All the nodes in the cluster.
   */
  def allNodes: SortedSet[Member] = reachabilities.keySet

  /**
   * All the nodes in the cluster with the given role. If `role` is the empty
   * string all nodes will be returned.
   *
   * @see [[allNodes]]
   */
  def allNodesWithRole(role: String): SortedSet[Member] =
    if (role != "") allNodes.filter(_.roles.contains(role)) else allNodes

  /**
   * Nodes that are reachable from the current node. Does not count weakly-up nodes
   * as they might not be visible from the other side of a potential split.
   */
  def reachableNodes: SortedSet[ReachableNode] =
    SortedSet(reachabilities.collect {
      case (member, Reachable) => ReachableNode(member)
    }.toSeq: _*)

  /**
   * Reachable nodes with the given role. If `role` is the empty
   * string all reachable nodes will be returned.
   *
   * @see [[reachableNodes]]
   */
  def reachableNodesWithRole(role: String): SortedSet[ReachableNode] =
    if (role != "") reachableNodes.filter(_.node.roles.contains(role)) else reachableNodes

  /**
   * Nodes that have been flagged as unreachable.
   */
  def unreachableNodes: SortedSet[UnreachableNode] =
    SortedSet(reachabilities.iterator.collect {
      case (member, Unreachable) => UnreachableNode(member)
    }.toSeq: _*)

  /**
   * Unreachable nodes with the given role. If `role` is the empty
   * string all unreachable nodes will be returned.
   *
   * @see [[unreachableNodes]]
   */
  def unreachableNodesWithRole(role: String): SortedSet[UnreachableNode] =
    if (role != "") unreachableNodes.filter(_.node.roles.contains(role)) else unreachableNodes

  /**
   * The reachability of the `member`.
   */
  def reachabilityOf(node: Member): Option[Reachability] = reachabilities.get(node)

  /**
   * Updates the reachability given the member event.
   *
   * Note:
   *   Reachability events might convey the same information as a member event.
   *   However, since [[reachabilityEvent()]] and [[memberEvent()]] are idempotent
   *   this is not a problem.
   */
  def memberEvent(event: MemberEvent): WorldView = event match {
    case MemberJoined(member) => becomeReachable(member)
    case MemberUp(member)     => becomeReachable(member)
    case MemberLeft(member)   => becomeReachable(member)
    case MemberExited(member) => becomeReachable(member)
    case MemberDowned(member) => becomeReachable(member)

    // Weakly up members should not be counted as they are not visible from the other side.
    case MemberWeaklyUp(_) => this

    case MemberRemoved(member, _) => remove(member)
  }

  /**
   * Updates the reachability given the reachability event.
   *
   * Note:
   *   Reachability events might convey the same information as a member event.
   *   However, since [[reachabilityEvent()]] and [[memberEvent()]] are idempotent
   *   this is not a problem.
   */
  def reachabilityEvent(event: ReachabilityEvent): WorldView = event match {
    case UnreachableMember(member) => becomeUnreachable(member)
    case ReachableMember(member)   => becomeReachable(member)
  }

  private def remove(member: Member): WorldView = new WorldView(reachabilities - member)

  private def becomeUnreachable(member: Member): WorldView = new WorldView(reachabilities + (member -> Unreachable))
  private def becomeReachable(member: Member): WorldView   = new WorldView(reachabilities + (member -> Reachable))
}

object WorldView {
  def apply(state: CurrentClusterState): WorldView = {
    val unreachableMembers: SortedMap[Member, Unreachable.type] =
      state.unreachable
        .map(_ -> Unreachable)(collection.breakOut)

    val reachableMembers: SortedMap[Member, Reachable.type] =
      state.members
        .diff(state.unreachable)
        .filterNot(_.status == WeaklyUp)
        .map(_ -> Reachable)(collection.breakOut)

    new WorldView(unreachableMembers ++ reachableMembers)
  }
}
