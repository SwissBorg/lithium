package akka.cluster.sbr

import akka.cluster.Member
import cats.Order

final case class UnreachableNode(node: Member) extends AnyVal

object UnreachableNode {
  implicit val unreachableNodeOrdering: Ordering[UnreachableNode] = Ordering.by(_.node)
  implicit val unreachableNodeOrder: Order[UnreachableNode]       = Order.fromOrdering
}
