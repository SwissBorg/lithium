package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.{ReachableNode, UnreachableNode, WorldView}

import scala.collection.immutable.SortedSet

sealed abstract private[keepmajority] class NodesMajority extends Product with Serializable

private[keepmajority] object NodesMajority {
  def apply(worldView: WorldView): NodesMajority = {
    val totalNodes = worldView.allNodes.size
    val majority = if (totalNodes == 0) 0 else totalNodes / 2 + 1
    val reachableNodes = worldView.reachableNodes
    val unreachableNodes = worldView.unreachableNodes

    if (reachableNodes.size >= majority) ReachableMajority(reachableNodes)
    else if (unreachableNodes.size >= majority) UnreachableMajority(unreachableNodes)
    else NoMajority
  }
}

final private[keepmajority] case class ReachableMajority(reachableNodes: SortedSet[ReachableNode]) extends NodesMajority

final private[keepmajority] case class UnreachableMajority(unreachableNodes: SortedSet[UnreachableNode])
    extends NodesMajority

final private[keepmajority] case object NoMajority extends NodesMajority
