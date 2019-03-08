package akka.cluster.sbr

import akka.cluster.ClusterEvent.{ReachableMember => AkkaReachableMember, UnreachableMember => AkkaUnreachableMember, _}
import akka.cluster.Member
import akka.cluster.MemberStatus.WeaklyUp
import cats.data.NonEmptyMap
import cats.implicits._
import akka.cluster.Member._

import scala.collection.SortedMap
import scala.collection.SortedSet

/**
 * Tracks the reachable and unreachable nodes given the [[MemberEvent]]s and [[ReachabilityEvent]]s.
 */
final case class Reachability private[sbr] (private[sbr] val m: SortedMap[Member, ReachabilityTag]) {

  /**
   * Nodes that are reachable from the current node. Does not count weakly-up nodes
   * as they might not be visible from the other side of a potential split.
   */
  def reachableNodes: SortedSet[ReachableNode] =
    SortedSet(m.collect {
      case (member, Reachable) => ReachableNode(member)
    }.toSeq: _*)

  /**
   * Nodes that have been flagged as unreachable.
   */
  def unreachableNodes: SortedSet[UnreachableNode] =
    SortedSet(m.iterator.collect {
      case (member, Unreachable) => UnreachableNode(member)
    }.toSeq: _*)

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
    case AkkaUnreachableMember(member) => becomeUnreachable(member)
    case AkkaReachableMember(member)   => becomeReachable(member)
  }

  private def remove(member: Member): Reachability = new Reachability(m - member)

  private def becomeUnreachable(member: Member): Reachability = new Reachability(m + (member -> Unreachable))

  private def becomeReachable(member: Member): Reachability = new Reachability(m + (member -> Reachable))
}

object Reachability {
  def apply(state: CurrentClusterState): Reachability = {
    val unreachableMembers: SortedMap[Member, Unreachable.type] =
      state.unreachable
        .map(_ -> Unreachable)(collection.breakOut)

    val reachableMembers: SortedMap[Member, Reachable.type] =
      state.members
        .diff(state.unreachable)
        .filterNot(_.status == WeaklyUp)
        .map(_ -> Reachable)(collection.breakOut)

    new Reachability(unreachableMembers ++ reachableMembers)
  }
}
