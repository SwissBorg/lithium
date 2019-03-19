package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.{UnreachableNode, WorldView}
import cats.data.NonEmptySet
import eu.timepit.refined.auto._

import scala.collection.immutable.SortedSet

sealed abstract private[staticquorum] class UnreachableNodes

private[staticquorum] object UnreachableNodes {
  def apply(worldView: WorldView, quorumSize: QuorumSize, role: String): UnreachableNodes = {
    val unreachableNodes = worldView.unreachableNodesWithRole(role)

    if (unreachableNodes.isEmpty) (new EmptyUnreachable() {})
    else {
      // We know `unreachableNodes` is non-empty.
      val nonEmptyNodes = NonEmptySet.fromSetUnsafe(SortedSet.empty[UnreachableNode] ++ worldView.unreachableNodes)

      if (unreachableNodes.size >= quorumSize)
        (new UnreachablePotentialQuorum(nonEmptyNodes) {})
      else
        (new UnreachableSubQuorum(nonEmptyNodes) {})
    }
  }
}

sealed abstract private[staticquorum] case class UnreachablePotentialQuorum(
  unreachableNodes: NonEmptySet[UnreachableNode]
) extends UnreachableNodes

sealed abstract private[staticquorum] case class UnreachableSubQuorum(
  unreachableNodes: NonEmptySet[UnreachableNode]
) extends UnreachableNodes

sealed abstract private[staticquorum] case class EmptyUnreachable() extends UnreachableNodes
