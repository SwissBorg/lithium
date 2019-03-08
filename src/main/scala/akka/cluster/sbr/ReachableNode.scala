package akka.cluster.sbr

import akka.cluster.Member

final case class ReachableNode(node: Member) extends AnyVal

object ReachableNode {
  implicit val reachableNodeOrdering: Ordering[ReachableNode] = Ordering.by(_.node)
}
