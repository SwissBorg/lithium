package akka.cluster.sbr

import cats.data.NonEmptySet
import eu.timepit.refined.auto._

import scala.collection.immutable.SortedSet

sealed abstract class UnreachableNodes

object UnreachableNodes {
  def apply(reachability: Reachability, quorumSize: QuorumSize): UnreachableNodes = {
    val unreachableNodes = reachability.unreachableNodes

    if (unreachableNodes.isEmpty) (new EmptyUnreachable() {})
    else {
      // We know `unreachableNodes` is non-empty.
      val nonEmptyNodes = NonEmptySet.fromSetUnsafe(SortedSet.empty[UnreachableNode] ++ unreachableNodes)

      if (unreachableNodes.size >= quorumSize)
        (new UnreachablePotentialQuorum(nonEmptyNodes) {})
      else
        (new UnreachableSubQuorum(nonEmptyNodes) {})
    }
  }
}

sealed abstract case class UnreachablePotentialQuorum(unreachableNodes: NonEmptySet[UnreachableNode])
    extends UnreachableNodes

sealed abstract case class UnreachableSubQuorum(unreachableNodes: NonEmptySet[UnreachableNode]) extends UnreachableNodes

sealed abstract case class EmptyUnreachable() extends UnreachableNodes
