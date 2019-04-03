package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.Member._
import akka.cluster.MemberStatus.{Joining, WeaklyUp}
import akka.cluster.sbr.implicits._
import cats.Eq
import cats.data.NonEmptyMap
import cats.implicits._

import scala.collection.immutable.{SortedMap, SortedSet}

/**
 * The cluster from the point of view of a node.
 */
final case class WorldView private[sbr] (private[sbr] val self: Member,
                                         private[sbr] val selfStatus: Status,
                                         private[sbr] val otherStatuses: SortedMap[Member, Status]) {

  /**
   * All the nodes in the cluster.
   */
  def allConsideredNodes: SortedSet[Member] =
    SortedSet(allStatuses.toSortedMap.collect {
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
    SortedSet(allStatuses.toSortedMap.collect {
      case (member, Reachable) => ReachableConsideredNode(member)
    }.toSeq: _*)

  /**
   * Reachable nodes with the given role. If `role` is the empty
   * string all reachable nodes will be returned.
   *
   * @see [[reachableConsideredNodes]]
   */
  def reachableConsideredNodesWithRole(role: String): SortedSet[ReachableConsideredNode] =
    if (role != "") reachableConsideredNodes.filter(_.member.roles.contains(role)) else reachableConsideredNodes

  def reachableNodes: SortedSet[ReachableNode] =
    SortedSet(allStatuses.toSortedMap.collect {
      case (member, Reachable)       => ReachableNode(member)
      case (member, WeaklyReachable) => ReachableNode(member)
      case (member, Staged)          => ReachableNode(member)
    }.toSeq: _*)

  /**
   * Nodes that have been flagged as unreachable.
   */
  def unreachableNodes: SortedSet[UnreachableNode] =
    SortedSet(allStatuses.toSortedMap.iterator.collect {
      case (member, Unreachable) => UnreachableNode(member)
    }.toSeq: _*)

  /**
   * Unreachable nodes with the given role. If `role` is the empty
   * string all unreachable nodes will be returned.
   *
   * @see [[unreachableNodes]]
   */
  def unreachableNodesWithRole(role: String): SortedSet[UnreachableNode] =
    if (role != "") unreachableNodes.filter(_.member.roles.contains(role)) else unreachableNodes

  /**
   * The reachability of the `member`.
   */
  def statusOf(node: Member): Option[Status] = allStatuses.lookup(node)

  /**
   * Updates the reachability given the member event.
   */
  def memberEvent(event: MemberEvent): WorldView =
    event match {
      case MemberJoined(node)              => join(node)
      case MemberWeaklyUp(node)            => weaklyUp(node)
      case MemberUp(member)                => up(member)
      case _: MemberLeft | _: MemberExited => leftOrExited(event.member)
      case MemberDowned(member)            => down(member)
      case MemberRemoved(member, _)        => remove(member)
    }

  /**
   * Updates the reachability given the reachability event.
   */
  def reachabilityEvent(event: ReachabilityEvent): WorldView =
    event match {
      case UnreachableMember(member) => becomeUnreachable(member)
      case ReachableMember(member)   => becomeReachable(member)
    }

  // todo
  def isStableChange(oldWorldView: WorldView): Boolean = (oldWorldView.unreachableNodes -- unreachableNodes).isEmpty

  /**
   * Stages the `node`.
   *
   * A staged node is a node that has been seen by the the current
   * node but should not be counted in the decisions. E.g. in the
   * `Joining` status.
   *
   */
  private def join(node: Member): WorldView =
    if (node === self) {
      copy(self = node)
    } else {
      copy(otherStatuses = otherStatuses + (node -> Staged))
    }

  /**
   * Stages the `node`.
   *
   * A staged node is a node that has been seen by the the current
   * node but should not be counted in the decisions. E.g. in the
   * `WeaklyUp`  status.
   *
   */
  private def weaklyUp(node: Member): WorldView =
    if (node === self) {
      copy(self = node, selfStatus = WeaklyReachable)
    } else {
      copy(otherStatuses = otherStatuses + (node -> WeaklyReachable))
    }

  /**
   * todo
   */
  private def down(node: Member): WorldView =
    if (self === node) {
      copy(self = node)
    } else {
      statusOf(node).fold(this)(status => copy(otherStatuses = otherStatuses + (node -> status)))
    }

  /**
   * Makes a staged node `Reachable`.
   */
  private def up(node: Member): WorldView =
    if (node === self) {
      copy(self = node, selfStatus = Reachable)
    } else {
      copy(otherStatuses = otherStatuses + (node -> Reachable))
    }

  /**
   * Updates the member.
   */
  private def leftOrExited(node: Member): WorldView =
    if (node === self) {
      copy(self = node)
    } else {
      statusOf(node).fold(this)(
        status => copy(otherStatuses = otherStatuses + (node -> status))
      )
    }

  /**
   * Remove the `node`.
   */
  private def remove(node: Member): WorldView =
    // ignores self removal
    copy(otherStatuses = otherStatuses - node)

  /**
   * Change the `node`'s status to `Unreachable`.
   */
  private def becomeUnreachable(node: Member): WorldView = changeStatus(Unreachable, node)

  /**
   * Change the `node`'s state to `Reachable`.
   */
  private def becomeReachable(node: Member): WorldView = changeStatus(Reachable, node)

  private def changeStatus(status: Status, node: Member): WorldView =
    if (node === self) {
      copy(self = node, selfStatus = status)
    } else {
      copy(otherStatuses = otherStatuses + (node -> status))
    }

  private[sbr] val allStatuses: NonEmptyMap[Member, Status] = NonEmptyMap(self -> selfStatus, otherStatuses)
}

object WorldView {
  def init(self: Member): WorldView = WorldView(self, Staged, SortedMap(self -> Staged))

  // todo test
  def apply(self: Member, state: CurrentClusterState): WorldView = {
    val unreachableMembers: SortedMap[Member, Unreachable.type] =
      state.unreachable
        .map(_ -> Unreachable)(collection.breakOut)

    val reachableMembers: SortedMap[Member, Status] =
      state.members
        .diff(state.unreachable)
        .map { m =>
          m.status match {
            case Joining  => m -> Staged
            case WeaklyUp => m -> WeaklyReachable
            case _        => m -> Reachable
          }
        }(collection.breakOut)

    val a = WorldView(self,
                      reachableMembers.getOrElse(self, Staged),
                      unreachableMembers ++ reachableMembers.filterKeys(_ =!= self)) // Self is added separately]

    println(s"INIT $a")

    a
  }

  implicit val worldViewEq: Eq[WorldView] = new Eq[WorldView] {
    override def eqv(x: WorldView, y: WorldView): Boolean =
      x.self === y.self && x.selfStatus === y.selfStatus && x.otherStatuses === y.otherStatuses
  }
}
