package akka.sbr

import akka.cluster.Member
import cats.data.NonEmptySet
import eu.timepit.refined.auto._

import scala.collection.immutable.SortedSet

sealed abstract class UnreachableNodeGroup

object UnreachableNodeGroup {
  def apply[S: ClusterState](state: S, quorumSize: QuorumSize): UnreachableNodeGroup = {
    val unreachableNodes = ClusterState[S].unreachableNodes(state)

    if (unreachableNodes.isEmpty) (new EmptyUnreachable() {})
    else {
      // We know `unreachableNodes` is non-empty.
      val nonEmptyNodes = NonEmptySet.fromSetUnsafe(SortedSet.empty[Member] ++ unreachableNodes)

      if (unreachableNodes.size >= quorumSize)
        (new UnreachablePotentialQuorum(nonEmptyNodes) {})
      else
        (new UnreachableSubQuorum(nonEmptyNodes) {})
    }
  }
}

sealed abstract case class UnreachablePotentialQuorum(nodes: NonEmptySet[Member]) extends UnreachableNodeGroup
sealed abstract case class UnreachableSubQuorum(nodes: NonEmptySet[Member]) extends UnreachableNodeGroup
sealed abstract case class EmptyUnreachable() extends UnreachableNodeGroup
