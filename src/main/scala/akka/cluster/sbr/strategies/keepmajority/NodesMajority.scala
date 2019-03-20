package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.Member
import akka.cluster.sbr._
import cats.implicits._

sealed abstract private[keepmajority] class NodesMajority extends Product with Serializable

private[keepmajority] object NodesMajority {
  def apply(worldView: WorldView, role: String): Either[NodesMajorityError, NodesMajority] = {
    val totalNodes = worldView.allConsideredNodesWithRole(role).size

    val majority                 = if (totalNodes == 0) 1 else totalNodes / 2 + 1
    val reachableConsideredNodes = worldView.reachableConsideredNodesWithRole(role)
    val unreachableNodes         = worldView.unreachableNodesWithRole(role)

    if (reachableConsideredNodes.size >= majority) ReachableMajority.asRight
    else if (unreachableNodes.size >= majority) UnreachableMajority.asRight
    else if (reachableConsideredNodes.size == unreachableNodes.size) {
      (for {
        lowestAddressNode <- worldView
          .allConsideredNodesWithRole(role)
          .toList
          .sortBy(_.address)(Member.addressOrdering)
          .headOption

        reachability <- worldView.statusOf(lowestAddressNode)
      } yield
        reachability match {
          case Reachable   => ReachableLowestAddress.asRight
          case Unreachable => UnreachableLowestAddress.asRight
          case Staged      => LowestAddressIsStaged(lowestAddressNode).asLeft
        }).getOrElse(NoMajority.asLeft) // no reachable nor unreachable nodes

    } else NoMajority.asLeft
  }

  sealed abstract class NodesMajorityError(message: String) extends Throwable(message)
  final case class LowestAddressIsStaged(node: Member)      extends NodesMajorityError(s"$node")
  final object NoMajority                                   extends NodesMajorityError("")
}

final private[keepmajority] case object ReachableMajority        extends NodesMajority
final private[keepmajority] case object UnreachableMajority      extends NodesMajority
final private[keepmajority] case object ReachableLowestAddress   extends NodesMajority
final private[keepmajority] case object UnreachableLowestAddress extends NodesMajority
