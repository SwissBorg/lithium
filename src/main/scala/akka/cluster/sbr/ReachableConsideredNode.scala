package akka.cluster.sbr

import akka.cluster.Member
import cats.{Eq, Order}

final case class ReachableConsideredNode(node: Member) extends AnyVal

object ReachableConsideredNode {
  implicit val reachableConsideredNodeOrdering: Ordering[ReachableConsideredNode] = Ordering.by(_.node)

  implicit val reachableConsideredNodeOrder: Order[ReachableConsideredNode] = Order.fromOrdering

  implicit val reachableConsideredNodeEq: Eq[ReachableConsideredNode] =
    (x: ReachableConsideredNode, y: ReachableConsideredNode) => reachableConsideredNodeOrdering.equiv(x, y)
}
