package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.MemberStatus.WeaklyUp

import scala.collection.immutable.SortedSet

/**
 * Tracks the reachable and unreachable nodes given the [[MemberEvent]]s and [[ReachabilityEvent]]s.
 *
 * @param reachableNodes nodes that are reachable from the current node. Does not count weakly-up nodes
 *                       as they might not be visible from the other side of a potential split.
 * @param unreachableNodes nodes that have flagged as unreachable.
 */
final case class Reachability private (reachableNodes: SortedSet[Member], unreachableNodes: Set[Member]) {

  /**
   * Updates the reachability given the member event.
   *
   * Note:
   *   Reachability events might convey the same information as a member event.
   *   However, since [[reachabilityEvent()]] and [[memberEvent()]] are idempotent
   *   this is not a problem.
   */
  def memberEvent(event: MemberEvent): Reachability = event match {
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
  def reachabilityEvent(event: ReachabilityEvent): Reachability = event match {
    case UnreachableMember(member) => becomeUnreachable(member)
    case ReachableMember(member)   => becomeReachable(member) // TODO should remove
  }

  private def becomeUnreachable(member: Member): Reachability =
    Reachability(reachableNodes - member, unreachableNodes + member)

  private def becomeReachable(member: Member): Reachability =
    Reachability(reachableNodes + member, unreachableNodes - member)

  private def remove(member: Member): Reachability =
    Reachability(reachableNodes - member, unreachableNodes - member)
}

object Reachability {
  def apply(state: CurrentClusterState): Reachability =
    Reachability(state.members.diff(state.unreachable).filterNot(_.status == WeaklyUp), state.unreachable)
}
