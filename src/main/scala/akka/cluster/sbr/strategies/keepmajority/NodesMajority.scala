package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.Member
import akka.cluster.sbr._

import scala.collection.immutable.SortedSet

sealed abstract private[keepmajority] class NodesMajority extends Product with Serializable

private[keepmajority] object NodesMajority {
  def apply(worldView: WorldView, role: String): NodesMajority = {
    val totalNodes = worldView.allNodesWithRole(role).size

    val majority         = if (totalNodes == 0) 1 else totalNodes / 2 + 1
    val reachableNodes   = worldView.reachableNodesWithRole(role)
    val unreachableNodes = worldView.unreachableNodesWithRole(role)

    if (reachableNodes.size >= majority) ReachableMajority(reachableNodes)
    else if (unreachableNodes.size >= majority) UnreachableMajority(unreachableNodes)
    else if (reachableNodes.size == unreachableNodes.size) {
      (for {
        lowestAddressNode <- worldView
          .allNodesWithRole(role)
          .toList
          .sortBy(_.address)(Member.addressOrdering)
          .headOption

        reachability <- worldView
          .reachabilityOf(lowestAddressNode)
      } yield
        reachability match {
          case Reachable   => ReachableLowestAddress(reachableNodes)
          case Unreachable => UnreachableLowestAddress(unreachableNodes)
        }).getOrElse(NoMajority) // no reachable nor unreachable nodes

    } else NoMajority
  }
}

final private[keepmajority] case class ReachableMajority(reachableNodes: SortedSet[ReachableNode]) extends NodesMajority

final private[keepmajority] case class UnreachableMajority(unreachableNodes: SortedSet[UnreachableNode])
    extends NodesMajority

final private[keepmajority] case class ReachableLowestAddress(reachableNodes: SortedSet[ReachableNode])
    extends NodesMajority

final private[keepmajority] case class UnreachableLowestAddress(unreachableNodes: SortedSet[UnreachableNode])
    extends NodesMajority

final private[keepmajority] case object NoMajority extends NodesMajority
