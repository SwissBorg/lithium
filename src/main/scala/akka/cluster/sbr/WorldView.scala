package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.Member._
import akka.cluster.MemberStatus.{Joining, WeaklyUp}
import cats.implicits._

import scala.collection.immutable.{SortedMap, SortedSet}

/**
 * The cluster from the point of view of a node.
 */
final case class WorldView private[sbr] (private[sbr] val self: Member,
                                         private[sbr] val statuses: SortedMap[Member, Status]) {
  import WorldView._

  /**
   * All the nodes in the cluster.
   */
  def allConsideredNodes: SortedSet[Member] =
    SortedSet(statuses.collect {
      case (member, Reachable)   => member
      case (member, Unreachable) => member
    }.toSeq: _*)

  /**
   * All the nodes in the cluster with the given role. If `role` is the empty
   * string all nodes will be returned.
   *
   * @see [[allConsideredNodes]]
   */
  def allConsideredNodesWithRole(role: String): SortedSet[Member] =
    if (role != "") allConsideredNodes.filter(_.roles.contains(role)) else allConsideredNodes

  /**
   * Nodes that are reachable from the current node. Does not count weakly-up nodes
   * as they might not be visible from the other side of a potential split.
   */
  def reachableConsideredNodes: SortedSet[ReachableConsideredNode] =
    SortedSet(statuses.collect {
      case (member, Reachable) => ReachableConsideredNode(member)
    }.toSeq: _*)

  /**
   * Reachable nodes with the given role. If `role` is the empty
   * string all reachable nodes will be returned.
   *
   * @see [[reachableConsideredNodes]]
   */
  def reachableConsideredNodesWithRole(role: String): SortedSet[ReachableConsideredNode] =
    if (role != "") reachableConsideredNodes.filter(_.node.roles.contains(role)) else reachableConsideredNodes

  def reachableNodes: SortedSet[ReachableNode] =
    SortedSet(statuses.collect {
      case (member, Reachable) => ReachableNode(member)
      case (member, Staged)    => ReachableNode(member)
    }.toSeq: _*)

  /**
   * Nodes that have been flagged as unreachable.
   */
  def unreachableNodes: SortedSet[UnreachableNode] =
    SortedSet(statuses.iterator.collect {
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
  def statusOf(node: Member): Option[Status] = statuses.get(node)

  /**
   * Updates the reachability given the member event.
   */
  def memberEvent(event: MemberEvent): Either[WorldViewError, WorldView] =
//    println(s"EVENT: $event")
    event match {
      // A member could have joined only one side of a partition
      // so should not be counted in the decision making.
      case MemberJoined(member) => stage(member)

      // A member can become `WeaklyUp` during a partition
      // so should not be counted in the decision making.
      case MemberWeaklyUp(member) => doNotAddYet(member)

      // todo
      case MemberUp(member) => addAsReachable(member)

      //
      case MemberLeft(member) => doNotRemoveYet(member)

      case MemberExited(member) => doNotRemoveYet(member)

      // A member can only become `Down` if it was unreachable.
      case MemberDowned(member) => doNotRemoveYet(member)

      // Not part of the cluster anymore.
      case MemberRemoved(member, _) => remove(member)
    }

  /**
   * Updates the reachability given the reachability event.
   */
  def reachabilityEvent(event: ReachabilityEvent): Either[WorldViewError, WorldView] = event match {
    case UnreachableMember(member) => becomeUnreachable(member)
    case ReachableMember(member)   => becomeReachable(member)
  }

  private def stage(member: Member): Either[WorldViewError, WorldView] =
    if (member == self) {
      if (statuses.contains(member)) {
        this.asRight // already staged at construction
      } else {
        SelfShouldExist(self).asLeft
      }
    } else {
      statusOf(member)
        .fold[Either[NodeAlreadyJoined, WorldView]](copy(statuses = statuses + (member -> Staged)).asRight)(
          _ => NodeAlreadyJoined(member).asLeft
        )
    }

  private def doNotAddYet(member: Member): Either[WorldViewError, WorldView] =
    statusOf(member)
      .fold[Either[WorldViewError, WorldView]](copy(statuses = statuses + (member -> Staged)).asRight) { // todo
        case Staged      => this.asRight
        case Reachable   => NodeAlreadyCounted(member).asLeft
        case Unreachable => NodeAlreadyCounted(member).asLeft
      }

  private def doNotRemoveYet(member: Member): Either[WorldViewError, WorldView] =
    statusOf(member).fold[Either[WorldViewError, WorldView]](UnknownNode(member).asLeft) {
      case Staged      => NodeStillStaged(member).asLeft
      case Reachable   => this.asRight
      case Unreachable => this.asRight
    }

  private def addAsReachable(member: Member): Either[NodeAlreadyCounted, WorldView] =
    statusOf(member)
      .fold[Either[NodeAlreadyCounted, WorldView]](copy(statuses = statuses + (member -> Reachable)).asRight) {
        case Staged      => copy(statuses = statuses + (member -> Reachable)).asRight
        case Reachable   => NodeAlreadyCounted(member).asLeft
        case Unreachable => NodeAlreadyCounted(member).asLeft
      }

  private def remove(member: Member): Either[UnknownNode, WorldView] =
    if (statuses.contains(member)) {
      copy(statuses = statuses - member).asRight
    } else {
      UnknownNode(member).asLeft
    }

  private def becomeUnreachable(member: Member): Either[UnknownNode, WorldView] =
    if (statuses.contains(member)) {
      copy(statuses = statuses + (member -> Unreachable)).asRight
    } else {
      UnknownNode(member).asLeft
    }

  private def becomeReachable(member: Member): Either[WorldViewError, WorldView] =
    statusOf(member).fold[Either[WorldViewError, WorldView]](UnknownNode(member).asLeft) {
      case Unreachable => copy(statuses = statuses + (member -> Reachable)).asRight
      case Reachable   => NodeAlreadyReachable(member).asLeft
      case Staged      => NodeStillStaged(member).asLeft
    }
}

object WorldView {
  def init(self: Member): WorldView = WorldView(self, SortedMap(self -> Staged))

  def apply(self: Member, state: CurrentClusterState): WorldView = {
    val unreachableMembers: SortedMap[Member, Unreachable.type] =
      state.unreachable
        .map(_ -> Unreachable)(collection.breakOut)

    val reachableMembers: SortedMap[Member, Reachable.type] =
      state.members
        .diff(state.unreachable)
        .filterNot(m => m.status == Joining || m.status == WeaklyUp)
        .map(_ -> Reachable)(collection.breakOut)

//    println(s"PREADD: ${unreachableMembers ++ reachableMembers}")

    // First add `self -> Staged` so it can be overridden by the other maps
    // if it appears in them. This is the case when the receive snapshot
    // already contains `self` as joined.
    WorldView(self, SortedMap(self -> Staged) ++ unreachableMembers ++ reachableMembers)
  }

  sealed abstract class WorldViewError(message: String) extends Throwable(message)
  final case class UnknownNode(member: Member)          extends WorldViewError(s"$member")
  final case class NodeAlreadyJoined(member: Member)    extends WorldViewError(s"$member")
  final case class NodeStillStaged(member: Member)      extends WorldViewError(s"$member")
  final case class NodeAlreadyCounted(member: Member)   extends WorldViewError(s"$member")
  final case class NodeAlreadyReachable(member: Member) extends WorldViewError(s"$member")
  final case class NodeAlreadyStaged(member: Member)    extends WorldViewError(s"$member")
  final case class SelfShouldExist(member: Member)      extends WorldViewError(s"$member")
}
