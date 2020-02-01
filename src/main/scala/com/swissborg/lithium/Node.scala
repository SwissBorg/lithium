package com.swissborg.lithium

import akka.actor.Address
import akka.cluster.{Member, MemberStatus, UniqueAddress}
import cats.{Order, Show}
import cats.implicits._

/**
 * Wrapper around a member adding the reachability information
 * on top of it.
 */
sealed abstract private[lithium] class Node extends Product with Serializable {
  val member: Member

  val status: MemberStatus         = member.status
  val uniqueAddress: UniqueAddress = member.uniqueAddress
  val address: Address             = member.address

  /**
   * Replace the current member by `member`.
   */
  def copyMember(member: Member): Node

  // hashCode + equals overridden so that only the unique addresses
  // are taken in account. All the other parts represent the state
  // of the node. Without this the same node, in two different
  // states, would be seen as two different ones.
  override def hashCode: Int = member.hashCode()

  override def equals(other: Any): Boolean = other match {
    case n: Node => member.equals(n.member)
    case _       => false
  }
}

private[lithium] object Node {
  implicit val nodeOrdering: Ordering[Node] = Ordering.by(_.member)
  implicit val nodeOrder: Order[Node]       = Order.fromOrdering
  implicit val nodeShow: Show[Node] = Show.show { node =>
    val member = node.member
    s"${member.toString().dropRight(1)}, roles = ${member.roles.mkString("[", ", ", "]")})"
  }
}

sealed private[lithium] trait NonIndirectlyConnectedNode extends Node

private[lithium] object NonIndirectlyConnectedNode {
  implicit val nonIndirectlyConnectedNodeOrdering: Ordering[NonIndirectlyConnectedNode] =
    Ordering.by(_.member)

  implicit val nonIndirectlyConnectedNodeOrder: Order[NonIndirectlyConnectedNode] =
    Order.fromOrdering
}

/**
 * A cluster node that cannot be reached from any of its observers.
 */
final private[lithium] case class UnreachableNode(member: Member) extends NonIndirectlyConnectedNode {
  override def copyMember(member: Member): Node = copy(member = member)
}

private[lithium] object UnreachableNode {
  implicit val unreachableNodeOrdering: Ordering[UnreachableNode] = Ordering.by(_.member)
  implicit val unreachableNodeOrder: Order[UnreachableNode]       = Order.fromOrdering
  implicit val unreachableNodeShow: Show[UnreachableNode]         = Show[Node].contramap(identity)
}

/**
 * A cluster nodes that can be reached by all its observers.
 */
final private[lithium] case class ReachableNode(member: Member) extends NonIndirectlyConnectedNode {
  override def copyMember(member: Member): Node = copy(member = member)
}

private[lithium] object ReachableNode {
  implicit val reachableNodeOrdering: Ordering[ReachableNode] = Ordering.by(_.member)
  implicit val reachableNodeOrder: Order[ReachableNode]       = Order.fromOrdering
  implicit val reachableNodeShow: Show[ReachableNode]         = Show[Node].contramap(identity)
}

/**
 * A cluster node that can be reached by only a part of its observers.
 */
final private[lithium] case class IndirectlyConnectedNode(member: Member) extends Node {
  override def copyMember(member: Member): Node = copy(member = member)
}

private[lithium] object IndirectlyConnectedNode {
  implicit val indirectlyConnectedNodeOrdering: Ordering[IndirectlyConnectedNode] = Ordering.by(_.member)
  implicit val indirectlyConnectedNodeOrder: Order[IndirectlyConnectedNode]       = Order.fromOrdering
  implicit val indirectlyConnectedNodeShow: Show[IndirectlyConnectedNode]         = Show[Node].contramap(identity)
}
