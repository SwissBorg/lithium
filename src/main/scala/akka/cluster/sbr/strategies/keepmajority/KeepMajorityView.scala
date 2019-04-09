package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.Member
import akka.cluster.sbr._
import cats.implicits._

/**
 * The view of the world specific for the [[KeepMajority]] strategy.
 */
sealed abstract private[keepmajority] class KeepMajorityView extends Product with Serializable

private[keepmajority] object KeepMajorityView {
  def apply(worldView: WorldView, role: String): Either[NoMajority.type, KeepMajorityView] = {
    val totalNodes = worldView.consideredNodesWithRole(role).size

    val majority =
      if (totalNodes <= 0) {
        // makes sure that the partition will always down itself
        1
      } else {
        totalNodes / 2 + 1
      }

    val reachableConsideredNodes = worldView.consideredReachableNodesWithRole(role)
    val unreachableNodes         = worldView.consideredUnreachableNodesWithRole(role)

    if (reachableConsideredNodes.size >= majority) ReachableMajority.asRight
    else if (unreachableNodes.size >= majority) UnreachableMajority.asRight
    else if (reachableConsideredNodes.size === unreachableNodes.size) {
      // check if the node with the lowest address is in this partition
      worldView
        .consideredNodesWithRole(role)
        .toList
        .sortBy(_.member.address)(Member.addressOrdering)
        .headOption
        .fold[Either[NoMajority.type, KeepMajorityView]](NoMajority.asLeft) {
          case _: ReachableNode   => ReachableLowestAddress.asRight
          case _: UnreachableNode => UnreachableLowestAddress.asRight
        }
    } else NoMajority.asLeft
  }

  final object NoMajority extends Throwable
}

/**
 * This partition is a majority.
 */
final private[keepmajority] case object ReachableMajority extends KeepMajorityView

/**
 * The other partition is a majority. In other words, this partition is NOT a majority.
 */
final private[keepmajority] case object UnreachableMajority extends KeepMajorityView

/**
 * The lowest address node is in this partition.
 */
final private[keepmajority] case object ReachableLowestAddress extends KeepMajorityView

/**
 * The lowest address node is in the other partition. In other words, this partition
 * does NOT contain that node.
 */
final private[keepmajority] case object UnreachableLowestAddress extends KeepMajorityView
