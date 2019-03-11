package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.{UnreachableNode, WorldView}
import cats.data.NonEmptySet
import eu.timepit.refined.auto._

import scala.collection.immutable.SortedSet

sealed abstract private[staticquorum] class UnreachableNodes

private[staticquorum] object UnreachableNodes {
  def apply(reachability: WorldView, quorumSize: QuorumSize): UnreachableNodes = {
    val unreachableNodes = reachability.unreachableNodes

    if (unreachableNodes.isEmpty) (new EmptyUnreachable() {})
    else {
      // We know `unreachableNodes` is non-empty.
      val nonEmptyNodes = NonEmptySet.fromSetUnsafe(SortedSet.empty[UnreachableNode] ++ unreachableNodes)

      if (unreachableNodes.size >= quorumSize)
        (new StaticQuorumUnreachablePotentialQuorum(nonEmptyNodes) {})
      else
        (new StaticQuorumUnreachableSubQuorum(nonEmptyNodes) {})
    }
  }
}

sealed abstract private[staticquorum] case class StaticQuorumUnreachablePotentialQuorum(
  unreachableNodes: NonEmptySet[UnreachableNode]
) extends UnreachableNodes

sealed abstract private[staticquorum] case class StaticQuorumUnreachableSubQuorum(
  unreachableNodes: NonEmptySet[UnreachableNode]
) extends UnreachableNodes

sealed abstract private[staticquorum] case class EmptyUnreachable() extends UnreachableNodes
