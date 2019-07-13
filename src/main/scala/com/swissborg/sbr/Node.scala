package com.swissborg.sbr

import akka.actor.Address
import akka.cluster.{Member, MemberStatus, UniqueAddress}
import cats.Order

/**
  * Wrapper around a member adding the reachability information
  * on top of it.
  */
private[sbr] sealed abstract class Node extends Product with Serializable {
  val member: Member

  val status: MemberStatus = member.status
  val uniqueAddress: UniqueAddress = member.uniqueAddress
  val address: Address = member.address

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
    case _ ⇒ false
  }
}

private[sbr] object Node {
  implicit val nodeOrdering: Ordering[Node] = Ordering.by(_.member)
  implicit val nodeOrder: Order[Node] = Order.fromOrdering
}

private[sbr] sealed trait NonIndirectlyConnectedNode extends Node

private[sbr] object NonIndirectlyConnectedNode {
  implicit val nonIndirectlyConnectedNodeOrdering: Ordering[NonIndirectlyConnectedNode] =
    Ordering.by(_.member)

  implicit val nonIndirectlyConnectedNodeOrder: Order[NonIndirectlyConnectedNode] =
    Order.fromOrdering
}

/**
  * A cluster node that cannot be reached from any of its observers.
  */
private[sbr] final case class UnreachableNode(member: Member) extends NonIndirectlyConnectedNode {
  override def copyMember(member: Member): Node = copy(member = member)
}

private[sbr] object UnreachableNode {
  implicit val unreachableNodeOrdering: Ordering[UnreachableNode] = Ordering.by(_.member)
  implicit val unreachableNodeOrder: Order[UnreachableNode] = Order.fromOrdering
}

/**
  * A cluster nodes that can be reached by all its observers.
  */
private[sbr] final case class ReachableNode(member: Member) extends NonIndirectlyConnectedNode {
  override def copyMember(member: Member): Node = copy(member = member)
}

private[sbr] object ReachableNode {
  implicit val reachableNodeOrdering: Ordering[ReachableNode] = Ordering.by(_.member)
  implicit val reachableNodeOrder: Order[ReachableNode] = Order.fromOrdering
}

/**
  * A cluster node that can be reached by only a part of its observers.
  */
private[sbr] final case class IndirectlyConnectedNode(member: Member) extends Node {
  override def copyMember(member: Member): Node = copy(member = member)
}

private[sbr] object IndirectlyConnectedNode {
  implicit val indirectlyConnectedNodeOrdering: Ordering[IndirectlyConnectedNode] =
    Ordering.by(_.member)
  implicit val indirectlyConnectedNodeOrder: Order[IndirectlyConnectedNode] = Order.fromOrdering
}
