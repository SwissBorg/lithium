package akka.cluster.sbr

import akka.cluster.Member
import cats.implicits._
import cats.{Eq, Order}

sealed abstract class Node extends Product with Serializable {
  val member: Member
  def copyMember(member: Member): Node

  def updateMember(f: Member => Member): Node = copyMember(f(member))

  override def hashCode: Int = member.hashCode()
  override def equals(other: Any): Boolean = other match {
    case n: Node ⇒ member.equals(n.member)
    case _       ⇒ false
  }
}

object Node {
  implicit val nodeOrdering: Ordering[Node] = Ordering.by(_.member)
  implicit val nodeOrder: Order[Node]       = Order.fromOrdering

  implicit val nodeEq: Eq[Node] = new Eq[Node] {
    override def eqv(x: Node, y: Node): Boolean = (x, y) match {
      case (_: UnreachableNode, _: UnreachableNode)                 => x === y
      case (_: ReachableNode, _: ReachableNode)                     => x === y
      case (_: IndirectlyConnectedNode, _: IndirectlyConnectedNode) => x === y
      case _                                                        => false
    }
  }
}

final case class UnreachableNode(member: Member) extends Node {
  override def copyMember(member: Member): Node = copy(member = member)
}
object UnreachableNode {
  implicit val unreachableNodeOrdering: Ordering[UnreachableNode] = Ordering.by(_.member)
  implicit val unreachableNodeOrder: Order[UnreachableNode]       = Order.fromOrdering
}

final case class ReachableNode(member: Member) extends Node {
  override def copyMember(member: Member): Node = copy(member = member)
}
object ReachableNode {
  implicit val reachableNodeOrdering: Ordering[ReachableNode] = Ordering.by(_.member)
  implicit val reachableNodeOrder: Order[ReachableNode]       = Order.fromOrdering
}

final case class IndirectlyConnectedNode(member: Member) extends Node {
  override def copyMember(member: Member): Node = copy(member = member)
}
object IndirectlyConnectedNode {
  implicit val indirectlyConnectedNodeOrdering: Ordering[IndirectlyConnectedNode] = Ordering.by(_.member)
  implicit val indirectlyConnectedNodeOrder: Order[IndirectlyConnectedNode]       = Order.fromOrdering
}
