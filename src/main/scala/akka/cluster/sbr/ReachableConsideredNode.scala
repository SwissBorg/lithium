package akka.cluster.sbr

import akka.cluster.Member

final case class ReachableConsideredNode(node: Member) extends AnyVal

object ReachableConsideredNode {
  implicit val reachableConsideredNodeOrdering: Ordering[ReachableConsideredNode] = Ordering.by(_.node)
}
