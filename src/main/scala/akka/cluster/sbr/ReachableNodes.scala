package akka.cluster.sbr

import cats.data.NonEmptySet
import eu.timepit.refined.auto._

import scala.collection.immutable.SortedSet

sealed abstract class ReachableNodes extends Product with Serializable

object ReachableNodes {
  def apply(worldView: WorldView, quorumSize: QuorumSize): Either[NoReachableNodesError.type, ReachableNodes] = {
    val reachableNodes = worldView.reachableNodes

    if (reachableNodes.isEmpty) Left(NoReachableNodesError)
    else {
      // we know `reachableNodes` is non-empty
      val nonEmptyNodes = NonEmptySet.fromSetUnsafe(SortedSet.empty[ReachableNode] ++ reachableNodes)

      if (reachableNodes.size >= quorumSize)
        Right(new ReachableQuorum(nonEmptyNodes) {})
      else
        Right(new ReachableSubQuorum(nonEmptyNodes) {})
    }
  }
}

sealed abstract case class ReachableQuorum(reachableNodes: NonEmptySet[ReachableNode]) extends ReachableNodes
sealed abstract case class ReachableSubQuorum(reachableNodes: NonEmptySet[ReachableNode]) extends ReachableNodes

final case object NoReachableNodesError
