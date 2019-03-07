package akka.sbr

import akka.cluster.Member
import cats.data.NonEmptySet
import eu.timepit.refined.auto._

sealed abstract class ReachableNodeGroup extends Product with Serializable

object ReachableNodeGroup {
  def apply(reachability: Reachability,
            quorumSize: QuorumSize): Either[NoReachableNodesError.type, ReachableNodeGroup] = {
    val reachableNodes = reachability.reachableNodes

    if (reachableNodes.isEmpty) Left(NoReachableNodesError)
    else {
      // we know `reachableNodes` is non-empty
      val nonEmptyNodes = NonEmptySet.fromSetUnsafe(reachableNodes)

      if (reachableNodes.size >= quorumSize)
        Right(new ReachableQuorum(nonEmptyNodes) {})
      else
        Right(new ReachableSubQuorum(nonEmptyNodes) {})
    }
  }
}

sealed abstract case class ReachableQuorum(nodes: NonEmptySet[Member]) extends ReachableNodeGroup
sealed abstract case class ReachableSubQuorum(nodes: NonEmptySet[Member]) extends ReachableNodeGroup

final case object NoReachableNodesError
