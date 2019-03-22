package akka.cluster.sbr

import akka.cluster.Member
import cats.{Eq, Order}

sealed abstract class Node extends Product with Serializable {
  val node: Member
}

object Node {
  implicit val nodeOrdering: Ordering[Node] = Ordering.by(_.node)
  implicit val nodeOrder: Order[Node]       = Order.fromOrdering

  implicit val nodeEq: Eq[Node] = new Eq[Node] {
    override def eqv(x: Node, y: Node): Boolean = (x, y) match {
      case (_: UnreachableNode, _: UnreachableNode)                 => nodeOrdering.equiv(x, y)
      case (_: ReachableNode, _: ReachableNode)                     => nodeOrdering.equiv(x, y)
      case (_: ReachableConsideredNode, _: ReachableConsideredNode) => nodeOrdering.equiv(x, y)
      case _                                                        => false
    }
  }
}

final case class UnreachableNode(node: Member) extends Node
object UnreachableNode {
  implicit val unreachableNodeOrdering: Ordering[UnreachableNode] = Ordering.by(_.node)
  implicit val unreachableNodeEq: Eq[UnreachableNode]             = unreachableNodeOrdering.equiv(_, _)
}

final case class ReachableNode(node: Member) extends Node
object ReachableNode {
  implicit val reachableNodeOrdering: Ordering[ReachableNode] = Ordering.by(_.node)
  implicit val reachableNodeEq: Eq[ReachableNode]             = reachableNodeOrdering.equiv(_, _)
}

final case class ReachableConsideredNode(node: Member) extends Node
object ReachableConsideredNode {
  implicit val reachableConsideredNodeOrdering: Ordering[ReachableConsideredNode] = Ordering.by(_.node)
  implicit val reachableConsideredNodeEq: Eq[ReachableConsideredNode]             = reachableConsideredNodeOrdering.equiv(_, _)
}

final case class IndirectlyConnectedNode(node: Member) extends Node
object IndirectlyConnectedNode {
  implicit val indirectlyConnectedNodeOrdering: Ordering[IndirectlyConnectedNode] = Ordering.by(_.node)
  implicit val indirectlyConnectedNodeEq: Eq[IndirectlyConnectedNode]             = indirectlyConnectedNodeOrdering.equiv(_, _)

}
