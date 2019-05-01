package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.MemberStatus.{Joining, Removed, WeaklyUp}
import akka.cluster.sbr.implicits._
import cats.Eq
import cats.data.NonEmptySet
import cats.implicits._

/**
 * Represents the view of the cluster from the point of view of the
 * `selfNode`.
 */
final case class WorldView private[sbr] (
  private[sbr] val selfNode: Node,
  private[sbr] val selfSeenBy: Set[Address],
  /**
   * The ordering on nodes is defined on their unique address,
   * ignoring for instance the status.
   * As a result, it cannot contain duplicate nodes.
   *
   * Care needs need to be taken when replacing a node with one where
   * the status changed in the set. First it has it to be removed and
   * then added. Only adding it will not override the value as they
   * are equal given the ordering.
   */
  private[sbr] val otherNodes: Map[Node, Set[Address]],
  /**
   * Removed members are kept as the information
   * is useful to detect the case when the removal
   * might not have been seen by a partition.
   */
  private[sbr] val removedMembers: Map[Member, Set[Address]], // todo when to cleanup?
  private[sbr] val trackIndirectlyConnected: Boolean
) {

  /**
   * All the nodes in the cluster.
   */
  lazy val nodes: NonEmptySet[Node] = NonEmptySet.of(selfNode, otherNodes.keys.toSeq: _*)

  /**
   * The nodes that need to be considered in split-brain resolutions.
   *
   * A node is to be considered when it isn't in the "Joining" or "WeaklyUp"
   * states. These status are ignored since a node can join and become
   * weakly-up during a network-partition.
   */
  lazy val consideredNodes: Set[Node] =
    nodes.collect { case node if shouldBeConsidered(node) => node }

  /**
   * The nodes with the given role, that need to be considered in
   * split-brain resolutions.
   */
  def consideredNodesWithRole(role: String): Set[Node] =
    if (role.nonEmpty) consideredNodes.filter(_.member.roles.contains(role)) else consideredNodes

  /**
   * The reachable nodes that need to be considered in split-brain resolutions.
   */
  lazy val consideredReachableNodes: Set[ReachableNode] =
    reachableNodes.collect { case n if shouldBeConsidered(n) => n }

  /**
   * The reachable nodes with the given role, that need to be
   * considered in split-brain resolutions.
   */
  def consideredReachableNodesWithRole(role: String): Set[ReachableNode] =
    if (role.nonEmpty) consideredReachableNodes.filter(_.member.roles.contains(role)) else consideredReachableNodes

  /**
   * All the reachable nodes.
   */
  lazy val reachableNodes: Set[ReachableNode] = nodes.collect {
    case r: ReachableNode => r

    // Cannot be indirectly connected from pov of the node that cannot reach it.
    // So it is safe to transform it into a reachable for those wo do.
    case i: IndirectlyConnectedNode if !trackIndirectlyConnected => ReachableNode(i.member)
  }

  /**
   * All the unreachable nodes.
   */
  lazy val unreachableNodes: Set[UnreachableNode] = nodes.collect { case r: UnreachableNode => r }

  /**
   * All the indirectly connected nodes.
   */
  lazy val indirectlyConnectedNodes: Set[IndirectlyConnectedNode] = nodes.collect {
    case r: IndirectlyConnectedNode => r
  }

  /**
   * The unreachable nodes that need to be considered in split-brain resolutions.
   */
  lazy val consideredUnreachableNodes: Set[UnreachableNode] = unreachableNodes.collect {
    case n if shouldBeConsidered(n) => n
  }

  /**
   * The unreachable nodes with the given role, that need to be
   * considered in split-brain resolutions.
   */
  def consideredUnreachableNodesWithRole(role: String): Set[UnreachableNode] =
    if (role.nonEmpty) consideredUnreachableNodes.filter(_.member.roles.contains(role)) else consideredUnreachableNodes

  /**
   * Update the world view given the member event.
   */
  def memberEvent(event: MemberEvent, seenBy: Set[Address]): WorldView = event match {
    case MemberRemoved(member, _) => removeMember(member, seenBy)
    case _                        => updateMember(event.member, seenBy)
  }

  /**
   * Update the given the world view given the reachability event.
   */
  def reachabilityEvent(event: ReachabilityEvent): WorldView =
    event match {
      case UnreachableMember(member) => unreachableMember(member)
      case ReachableMember(member)   => reachableMember(member)
    }

  def indirectlyConnected(member: Member): WorldView =
    if (member === selfNode.member) {
      copy(selfNode = IndirectlyConnectedNode(member))
    } else {
      otherNodes
        .find(_._1.member === member)
        .fold(copy(otherNodes = otherNodes + (IndirectlyConnectedNode(member) -> Set.empty))) {
          case (node, seenBy) =>
            copy(otherNodes = otherNodes - node + (IndirectlyConnectedNode(member) -> seenBy))
        }
    }

  def seenBy(seenBy: Set[Address]): WorldView =
    copy(selfSeenBy = seenBy, otherNodes = otherNodes.mapValues(_ => seenBy))

  def wasSeenBy(node: Node): Set[Address] =
    if (node === selfNode) selfSeenBy
    else otherNodes.getOrElse(node, Set.empty)

  /**
   * Change the `node`'s status to `Unreachable`.
   */
  private def unreachableMember(member: Member): WorldView = updateNode(UnreachableNode(member))

  /**
   * Change the `node`'s state to `Reachable`.
   */
  private def reachableMember(member: Member): WorldView = updateNode(ReachableNode(member))

  private def updateMember(member: Member, seenBy: Set[Address]): WorldView =
    if (member === selfNode.member) {
      copy(selfNode = selfNode.copyMember(member), selfSeenBy = seenBy)
    } else {
      // Assumes the member is reachable if seen for the 1st time.
      otherNodes
        .find(_._1.member === member)
        .fold(copy(otherNodes = otherNodes + (ReachableNode(member) -> seenBy))) {
          case (node, _) =>
            copy(otherNodes = otherNodes - node + (node.copyMember(member) -> seenBy))
        }
    }

  private def removeMember(member: Member, seenBy: Set[Address]): WorldView =
    if (member === selfNode.member) {
      copy(selfNode = selfNode.copyMember(member)) // ignore only update // todo is it safe?
    } else {
      otherNodes.find(_._1.member === member).fold(this) {
        case (node, _) =>
          copy(otherNodes = otherNodes - node, removedMembers = removedMembers + (node.member -> seenBy))
      }
    }

  private def updateNode(node: Node): WorldView =
    if (node.member === selfNode.member) {
      copy(selfNode = node)
    } else {
      otherNodes.get(node).fold(copy(otherNodes = otherNodes + (node -> Set.empty))) { seenBy =>
        copy(otherNodes = otherNodes - node + (node -> seenBy))
      }
    }

  private def shouldBeConsidered(node: Node): Boolean = node match {
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
  def init(selfMember: Member, trackIndirectlyConnected: Boolean): WorldView =
    new WorldView(ReachableNode(selfMember), Set(selfMember.address), Map.empty, Map.empty, trackIndirectlyConnected)

  def fromSnapshot(selfMember: Member, trackIndirectlyConnected: Boolean, state: CurrentClusterState): WorldView = {
    val w = WorldView.init(selfMember, trackIndirectlyConnected)

    val w1 = (state.members -- state.unreachable).foldLeft(w) {
      case (w, member) =>
        member.status match {
          case Removed => w.reachableMember(member).removeMember(member, Set.empty)
          case _       => w.reachableMember(member)
        }
    }

    state.unreachable
      .foldLeft(w1) {
        case (w, member) =>
          member.status match {
            case Removed => w.unreachableMember(member).removeMember(member, Set.empty)
            case _       => w.unreachableMember(member)
          }
      }
      .copy(selfSeenBy = state.seenBy)
  }

  implicit val worldViewEq: Eq[WorldView] = new Eq[WorldView] {
    override def eqv(x: WorldView, y: WorldView): Boolean =
      x.selfNode === y.selfNode && x.selfNode === y.selfNode && x.otherNodes === y.otherNodes
  }
}
