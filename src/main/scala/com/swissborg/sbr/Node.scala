package com.swissborg.sbr

import akka.cluster.Member
import cats.Order

/**
 * Wrapper around a member adding the reachability information
 * on top of it.
 */
sealed abstract class Node extends Product with Serializable {
  val member: Member

  /**
   * Replace the current member by `member`.
   */
  def copyMember(member: Member): Node

  /**
   * Apply `f` on the member.
   */
  def updateMember(f: Member => Member): Node = copyMember(f(member))

  // hashCode + equals overridden so that only the unique addresses
  // are taken in account. All the other parts represent the state
  // of the node. Without this the same node, in two different
  // states, would be seen as two different ones.
  override def hashCode: Int = member.hashCode()
  override def equals(other: Any): Boolean = other match {
    case n: Node ⇒ member.equals(n.member)
    case _       ⇒ false
  }
}

object Node {
  implicit val nodeOrdering: Ordering[Node] = Ordering.by(_.member)
  implicit val nodeOrder: Order[Node]       = Order.fromOrdering
}

sealed trait CleanNode extends Node

object CleanNode {
  implicit val consideredNodeOrdering: Ordering[CleanNode] = Ordering.by(_.member)
  implicit val consideredNodeOrder: Order[CleanNode]       = Order.fromOrdering
}

/**
 * A cluster node that cannot be reached from any of its observers.
 */
final case class UnreachableNode(member: Member) extends CleanNode {
  override def copyMember(member: Member): Node = copy(member = member)
}

object UnreachableNode {
  implicit val unreachableNodeOrdering: Ordering[UnreachableNode] = Ordering.by(_.member)
  implicit val unreachableNodeOrder: Order[UnreachableNode]       = Order.fromOrdering
}

/**
 * A cluster nodes that can be reached by all its observers.
 */
final case class ReachableNode(member: Member) extends CleanNode {
  override def copyMember(member: Member): Node = copy(member = member)
}

object ReachableNode {
  implicit val reachableNodeOrdering: Ordering[ReachableNode] = Ordering.by(_.member)
  implicit val reachableNodeOrder: Order[ReachableNode]       = Order.fromOrdering
}

/**
 * A cluster node that can be reached by only a part of its observers.
 */
final case class IndirectlyConnectedNode(member: Member) extends Node {
  override def copyMember(member: Member): Node = copy(member = member)
}

object IndirectlyConnectedNode {
  implicit val indirectlyConnectedNodeOrdering: Ordering[IndirectlyConnectedNode] = Ordering.by(_.member)
  implicit val indirectlyConnectedNodeOrder: Order[IndirectlyConnectedNode]       = Order.fromOrdering
}
