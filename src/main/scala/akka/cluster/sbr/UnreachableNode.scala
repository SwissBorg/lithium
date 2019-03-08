package akka.cluster.sbr

import akka.cluster.Member

final case class UnreachableNode(node: Member) extends AnyVal

object UnreachableNode {
  implicit val unreachableNodeOrdering: Ordering[UnreachableNode] = Ordering.by(_.node)
}
