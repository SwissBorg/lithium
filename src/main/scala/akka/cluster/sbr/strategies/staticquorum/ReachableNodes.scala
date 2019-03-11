package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.{ReachableNode, WorldView}
import cats.data.NonEmptySet
import eu.timepit.refined.auto._

import scala.collection.immutable.SortedSet

sealed abstract private[staticquorum] class ReachableNodes extends Product with Serializable

private[staticquorum] object ReachableNodes {
  def apply(worldView: WorldView,
            quorumSize: QuorumSize): Either[NoReachableNodesError.type, ReachableNodes] = {
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

sealed abstract private[staticquorum] case class ReachableQuorum(reachableNodes: NonEmptySet[ReachableNode])
    extends ReachableNodes
sealed abstract private[staticquorum] case class ReachableSubQuorum(reachableNodes: NonEmptySet[ReachableNode])
    extends ReachableNodes

final case object NoReachableNodesError extends Throwable
