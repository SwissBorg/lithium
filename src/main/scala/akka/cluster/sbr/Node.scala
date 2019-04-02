package akka.cluster.sbr

import akka.cluster.Member
import cats.{Eq, Order}
import cats.implicits._

sealed abstract class Node extends Product with Serializable {
  val member: Member
}

object Node {
  implicit val nodeOrdering: Ordering[Node] = Ordering.by(_.member)
  implicit val nodeOrder: Order[Node]       = Order.fromOrdering

  implicit val nodeEq: Eq[Node] = new Eq[Node] {
    override def eqv(x: Node, y: Node): Boolean = (x, y) match {
      case (_: UnreachableNode, _: UnreachableNode)                 => x === y
      case (_: ReachableNode, _: ReachableNode)                     => x === y
      case (_: ReachableConsideredNode, _: ReachableConsideredNode) => x === y
      case _                                                        => false
    }
  }
}

final case class UnreachableNode(member: Member) extends Node
object UnreachableNode {
  implicit val unreachableNodeOrdering: Ordering[UnreachableNode] = Ordering.by(_.member)
  implicit val unreachableNodeEq: Eq[UnreachableNode]             = unreachableNodeOrdering.equiv(_, _)
}

final case class ReachableNode(member: Member) extends Node
object ReachableNode {
  implicit val reachableNodeOrdering: Ordering[ReachableNode] = Ordering.by(_.member)
  implicit val reachableNodeEq: Eq[ReachableNode]             = reachableNodeOrdering.equiv(_, _)
}

final case class ReachableConsideredNode(member: Member) extends Node
object ReachableConsideredNode {
  implicit val reachableConsideredNodeOrdering: Ordering[ReachableConsideredNode] = Ordering.by(_.member)
  implicit val reachableConsideredNodeEq: Eq[ReachableConsideredNode]             = reachableConsideredNodeOrdering.equiv(_, _)
}

final case class IndirectlyConnectedNode(member: Member) extends Node
object IndirectlyConnectedNode {
  implicit val indirectlyConnectedNodeOrdering: Ordering[IndirectlyConnectedNode] = Ordering.by(_.member)
  implicit val indirectlyConnectedNodeEq: Eq[IndirectlyConnectedNode]             = indirectlyConnectedNodeOrdering.equiv(_, _)

}
