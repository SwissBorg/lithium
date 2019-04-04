package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.MemberStatus.{Down, Joining, Removed, WeaklyUp}
import akka.cluster.sbr.implicits._
import cats.Eq
import cats.data.NonEmptySet
import cats.implicits._

import scala.collection.immutable.SortedSet

/**
 * The cluster from the point of view of a node.
 */
final case class WorldView private[sbr] (private[sbr] val selfNode: Node,
                                         /**
                                          * Nodes are stored in a SortedSet as it depends on the ordering
                                          * and not universal equality. The ordering on nodes is defined
                                          * on their unique address, ignoring for instance the status.
                                          * As a result, it cannot contain duplicate nodes.
                                          *
                                          * Care needs need to be taken when replacing a node with one where
                                          * the status changed in the set. First it has it to be removed and
                                          * then added. Only adding it will not override the value as they
                                          * are equal given the ordering.
                                          */
                                         private[sbr] val otherNodes: SortedSet[Node]) {
  import WorldView._

  lazy val nodes: NonEmptySet[Node] = NonEmptySet(selfNode, otherNodes)

  def consideredNodes: SortedSet[Node] = nodes.toSortedSet.collect {
    case status if shouldBeConsidered(status.member) => status
  }

  /**
   * All the nodes in the cluster with the given role. If `role` is the empty
   * string all nodes will be returned.
   *
   * @see [[consideredNodes]]
   */
  def consideredNodesWithRole(role: String): SortedSet[Node] =
    if (role.nonEmpty) consideredNodes.filter(_.member.roles.contains(role)) else consideredNodes

  lazy val consideredReachableNodes: SortedSet[ReachableNode] =
    nodes.collect {
      case r @ ReachableNode(member) if shouldBeConsidered(member) => r
    }

  /**
   * Reachable nodes with the given role. If `role` is the empty
   * string all reachable nodes will be returned.
   *
   * @see [[consideredReachableNodes]]
   */
  def consideredReachableNodesWithRole(role: String): SortedSet[ReachableNode] =
    if (role.nonEmpty) consideredReachableNodes.filter(_.member.roles.contains(role)) else consideredReachableNodes

  lazy val reachableNodes: SortedSet[ReachableNode] = nodes.toSortedSet.collect { case r: ReachableNode => r }

  /**
   * Nodes that have been flagged as unreachable.
   */
  lazy val unreachableNodes: SortedSet[UnreachableNode] = nodes.collect {
    case r: UnreachableNode => r
  }

  lazy val consideredUnreachableNodes: SortedSet[UnreachableNode] = nodes.collect {
    case r @ UnreachableNode(member) if shouldBeConsidered(member) => r
  }

  /**
   * Unreachable nodes with the given role. If `role` is the empty
   * string all unreachable nodes will be returned.
   *
   * @see [[unreachableNodes]]
   */
  def unreachableNodesWithRole(role: String): SortedSet[UnreachableNode] =
    if (role.nonEmpty) unreachableNodes.filter(_.member.roles.contains(role)) else unreachableNodes

  def considerdeUnreachableNodesWithRole(role: String): SortedSet[UnreachableNode] =
    if (role.nonEmpty) consideredUnreachableNodes.filter(_.member.roles.contains(role)) else consideredUnreachableNodes

  /**
   * Updates the reachability given the member event.
   */
  def memberEvent(event: MemberEvent): WorldView = updateMember(event.member)

  /**
   * Updates the reachability given the reachability event.
   */
  def reachabilityEvent(event: ReachabilityEvent): WorldView =
    event match {
      case UnreachableMember(member) => becomeUnreachable(member)
      case ReachableMember(member)   => becomeReachable(member)
    }

  /**
   * Returns true if it is stable compared to `oldWorldView`. Otherwise, returns false.
   */
  def isStableChange(oldWorldView: WorldView): Boolean =
    oldWorldView.unreachableNodes.size != unreachableNodes.size ||
      (unreachableNodes -- oldWorldView.unreachableNodes).isEmpty ||
      (oldWorldView.unreachableNodes -- unreachableNodes).isEmpty

  def hasSplitBrain: Boolean =
    unreachableNodes.exists {
      _.member.status match {
        case Down | Removed => false // down or removed nodes are already leaving the cluster
        case _              => true
      }
    }

  /**
   * Change the `node`'s status to `Unreachable`.
   */
  private def becomeUnreachable(member: Member): WorldView = updateNode(UnreachableNode(member))

  /**
   * Change the `node`'s state to `Reachable`.
   */
  private def becomeReachable(member: Member): WorldView = updateNode(ReachableNode(member))

  private def updateMember(member: Member): WorldView =
    if (member === selfNode.member) {
      copy(selfNode = selfNode.copyMember(member))
    } else {
      // Assumes the member is reachable if seen for the 1st time.
      otherNodes.find(_.member === member).fold(copy(otherNodes = otherNodes + ReachableNode(member))) { status =>
        copy(otherNodes = otherNodes - status + status.copyMember(member))
      }
    }

  private def updateNode(node: Node): WorldView =
    if (node.member === selfNode.member) {
      copy(selfNode = node)
    } else {
      copy(otherNodes = otherNodes - node + node) // todo explain
    }
}

object WorldView {
  def init(self: Member): WorldView = new WorldView(ReachableNode(self), SortedSet.empty)

  // todo test
  def apply(self: Member, state: CurrentClusterState): WorldView = {
    val unreachableMembers: SortedSet[UnreachableNode] = SortedSet(state.unreachable.map(UnreachableNode(_)).toSeq: _*)

    val reachableMembers: SortedSet[ReachableNode] = SortedSet(
      state.members.diff(state.unreachable).map(ReachableNode(_)).toSeq: _*
    )

    val a = WorldView(
      reachableMembers
        .find(_.member === self)
        .orElse(unreachableMembers.find(_.member === self))
        .getOrElse(ReachableNode(self)), // assume self is reachable
      (unreachableMembers ++ reachableMembers).filter(_.member =!= self)
    )

    println(s"INIT $a")

    a
  }

  def shouldBeConsidered(member: Member): Boolean = member.status != Joining && member.status != WeaklyUp

  implicit val worldViewEq: Eq[WorldView] = new Eq[WorldView] {
    override def eqv(x: WorldView, y: WorldView): Boolean =
      x.selfNode === y.selfNode && x.selfNode === y.selfNode && x.otherNodes === y.otherNodes
  }
}
