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
 * Represents the view of the cluster from the point of view of the
 * `selfNode`.
 *
 * @param selfNode the node from which the world is seen.
 * @param otherNodes all the other nodes knowns by the `selfNode`.
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
                                         private[sbr] val otherNodes: SortedSet[Node],
                                         private[sbr] val trackIndirectlyConnected: Boolean) {

  /**
   * All the nodes in the cluster.
   */
  lazy val nodes: NonEmptySet[Node] = NonEmptySet(selfNode, otherNodes)

  /**
   * The nodes that need to be considered in split-brain resolutions.
   *
   * A node is to be considered when it isn't in the "Joining" or "WeaklyUp"
   * states. These status are ignored since a node can join and become
   * weakly-up during a network-partition.
   */
  def consideredNodes: SortedSet[Node] =
    nodes.toSortedSet.collect { case node if shouldBeConsidered(node) => node }

  /**
   * The nodes with the given role, that need to be considered in
   * split-brain resolutions.
   */
  def consideredNodesWithRole(role: String): SortedSet[Node] =
    if (role.nonEmpty) consideredNodes.filter(_.member.roles.contains(role)) else consideredNodes

  /**
   * The reachable nodes that need to be considered in split-brain resolutions.
   */
  lazy val consideredReachableNodes: SortedSet[ReachableNode] =
    reachableNodes.collect { case n if shouldBeConsidered(n) => n }

  /**
   * The reachable nodes with the given role, that need to be
   * considered in split-brain resolutions.
   */
  def consideredReachableNodesWithRole(role: String): SortedSet[ReachableNode] =
    if (role.nonEmpty) consideredReachableNodes.filter(_.member.roles.contains(role)) else consideredReachableNodes

  /**
   * All the reachable nodes.
   */
  lazy val reachableNodes: SortedSet[ReachableNode] = nodes.toSortedSet.collect {
    case r: ReachableNode => r

    // Cannot be indirectly connected from pov of the node that cannot reach it.
    // So it is safe to transform it into a reachable for those wo do.
    case i: IndirectlyConnectedNode if !trackIndirectlyConnected => ReachableNode(i.member)
  }

  /**
   * All the unreachable nodes.
   */
  lazy val unreachableNodes: SortedSet[UnreachableNode] =
    nodes.collect { case r: UnreachableNode => r }

  /**
   * All the indirectly connected nodes.
   */
  lazy val indirectlyConnectedNodes: SortedSet[IndirectlyConnectedNode] =
    nodes.collect { case r: IndirectlyConnectedNode => r }

  /**
   * The unreachable nodes that need to be considered in split-brain resolutions.
   */
  lazy val consideredUnreachableNodes: SortedSet[UnreachableNode] =
    unreachableNodes.collect { case n if shouldBeConsidered(n) => n }

  /**
   * The unreachable nodes with the given role, that need to be
   * considered in split-brain resolutions.
   */
  def consideredUnreachableNodesWithRole(role: String): SortedSet[UnreachableNode] =
    if (role.nonEmpty) consideredUnreachableNodes.filter(_.member.roles.contains(role)) else consideredUnreachableNodes

  /**
   * Update the world view given the member event.
   */
  def memberEvent(event: MemberEvent): WorldView =
    event match {
      case MemberRemoved(member, _) => removeMember(member)
      case _                        => updateMember(event.member)

    }

  /**
   * Update the given the world view given the reachability event.
   */
  def reachabilityEvent(event: ReachabilityEvent): WorldView =
    event match {
      case UnreachableMember(member) => becomeUnreachable(member)
      case ReachableMember(member)   => becomeReachable(member)
    }

  def indirectlyConnected(member: Member): WorldView =
    if (member === selfNode.member) {
      copy(selfNode = IndirectlyConnectedNode(member))
    } else {
      otherNodes.find(_.member === member).fold(copy(otherNodes = otherNodes + IndirectlyConnectedNode(member))) {
        node =>
          copy(otherNodes = otherNodes - node + IndirectlyConnectedNode(member))
      }
    }

  /**
   * True when there is no change in membership and reachability. Else, false.
   */
  def isStableChange(oldWorldView: WorldView): Boolean = {
    val nodes0   = nodes.toNonEmptyList
    val oldNodes = oldWorldView.nodes.toNonEmptyList

    // Check if all the nodes with the same address are equal.
    lazy val sameMembershipAndReachability = (nodes0 ::: oldNodes).groupBy(_.member.address).values.forall { ns =>
      val n = ns.head

      n match {
        case ReachableNode(member) =>
          ns.tail.forall {
            case ReachableNode(innerMember) => member.status == innerMember.status
            case _                          => false
          }

        case UnreachableNode(member) =>
          ns.tail.forall {
            case UnreachableNode(innerMember) => member.status == innerMember.status
            case _                            => false
          }

        case IndirectlyConnectedNode(member) =>
          ns.tail.forall({
            case IndirectlyConnectedNode(innerMember) => member.status == innerMember.status
            case _                                    => false
          })
      }
    }

    nodes0.size == oldNodes.size && sameMembershipAndReachability
  }

  def hasIndirectlyConnectedNodes: Boolean = ???

  def hasSplitBrain: Boolean =
    (unreachableNodes.map(_.member.status).toList ::: indirectlyConnectedNodes.map(_.member.status).toList).exists {
      case Down | Removed => false // down or removed nodes are already leaving the cluster
      case _              => true
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
      otherNodes.find(_.member === member).fold(copy(otherNodes = otherNodes + ReachableNode(member))) { node =>
        copy(otherNodes = otherNodes - node + node.copyMember(member))
      }
    }

  private def removeMember(member: Member): WorldView =
    if (member === selfNode.member) {
      copy(selfNode = selfNode.copyMember(member)) // ignore only update
    } else {
      otherNodes.find(_.member === member).fold(this) { node =>
        copy(otherNodes = otherNodes - node)
      }
    }

  private def updateNode(node: Node): WorldView =
    if (node.member === selfNode.member) {
      copy(selfNode = node)
    } else {
      copy(otherNodes = otherNodes - node + node) // todo explain
    }

  def shouldBeConsidered(node: Node): Boolean = node match {
    case UnreachableNode(member) =>
      member.status != Joining && member.status != WeaklyUp

    case ReachableNode(member) =>
      member.status != Joining && member.status != WeaklyUp

    // When indirectly connected nodes are tracked they do not
    // appear in the considered nodes as they will be downed.
    case IndirectlyConnectedNode(member) =>
      !trackIndirectlyConnected && member.status != Joining && member.status != WeaklyUp
  }
}

object WorldView {
  def init(self: Member, trackIndirectlyConnected: Boolean): WorldView =
    new WorldView(ReachableNode(self), SortedSet.empty, trackIndirectlyConnected)

  // todo test
  def apply(self: Member, trackIndirectlyConnected: Boolean, state: CurrentClusterState): WorldView = {
    val unreachableMembers: SortedSet[UnreachableNode] = SortedSet(state.unreachable.map(UnreachableNode(_)).toSeq: _*)

    val reachableMembers: SortedSet[ReachableNode] = SortedSet(
      state.members.diff(state.unreachable).map(ReachableNode(_)).toSeq: _*
    )

    WorldView(
      reachableMembers
        .find(_.member === self)
        .orElse(unreachableMembers.find(_.member === self))
        .getOrElse(ReachableNode(self)), // assume self is reachable
      (unreachableMembers ++ reachableMembers).filter(_.member =!= self),
      trackIndirectlyConnected
    )
  }

  implicit val worldViewEq: Eq[WorldView] = new Eq[WorldView] {
    override def eqv(x: WorldView, y: WorldView): Boolean =
      x.selfNode === y.selfNode && x.selfNode === y.selfNode && x.otherNodes === y.otherNodes
  }
}
