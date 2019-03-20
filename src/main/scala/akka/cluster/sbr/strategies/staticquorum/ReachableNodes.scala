package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.WorldView
import cats.implicits._
import eu.timepit.refined.auto._

sealed abstract private[staticquorum] class ReachableNodes extends Product with Serializable

private[staticquorum] object ReachableNodes {
  def apply(worldView: WorldView,
            quorumSize: QuorumSize,
            role: String): Either[NoReachableNodesError.type, ReachableNodes] = {
    val reachableConsideredNodes = worldView.reachableConsideredNodesWithRole(role)

    if (worldView.reachableConsideredNodes.isEmpty && reachableConsideredNodes.isEmpty) {
//      println(s"WV: $worldView")
      NoReachableNodesError.asLeft
    } else {
      if (reachableConsideredNodes.size >= quorumSize)
        ReachableQuorum.asRight
      else
        ReachableSubQuorum.asRight
    }
  }
}

final private[staticquorum] case object ReachableQuorum    extends ReachableNodes
final private[staticquorum] case object ReachableSubQuorum extends ReachableNodes

final private[staticquorum] case object NoReachableNodesError extends Throwable
