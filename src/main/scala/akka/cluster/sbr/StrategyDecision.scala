package akka.cluster.sbr

import akka.actor.Address
import monocle.Getter

import scala.collection.immutable.SortedSet

/**
 * Represents the strategy that needs to be taken
 * to resolve a potential split-brain issue.
 */
sealed abstract class StrategyDecision extends Product with Serializable

object StrategyDecision {

  /**
   * Gets the addresses of the members that should be downed.
   */
  val addressesToDown: Getter[StrategyDecision, Set[Address]] = Getter[StrategyDecision, Set[Address]] {
    case DownReachable(reachableNodes)       => reachableNodes.map(_.node.address)
    case DownUnreachable(unreachableNodes)   => unreachableNodes.map(_.node.address)
    case UnsafeDownReachable(reachableNodes) => reachableNodes.map(_.node.address)
    case _: Idle.type                        => Set.empty
  }

  implicit class DecisionOps(private val decision: StrategyDecision) extends AnyVal {

    /**
     * The addresses of the members that should be downed.
     */
    def addressesToDown: Set[Address] = StrategyDecision.addressesToDown.get(decision)
  }
}

/**
 * The reachable nodes should be downed.
 */
sealed abstract case class DownReachable(nodeGroup: SortedSet[ReachableNode]) extends StrategyDecision
object DownReachable {
  def apply(worldView: WorldView): DownReachable = new DownReachable(worldView.reachableNodes) {}
}

/**
 * TODO doc!
 */
sealed abstract case class UnsafeDownReachable(nodeGroup: SortedSet[ReachableNode]) extends StrategyDecision
object UnsafeDownReachable {
  def apply(worldView: WorldView): UnsafeDownReachable = new UnsafeDownReachable(worldView.reachableNodes) {}
}

/**
 * The unreachable nodes should be downed.
 */
sealed abstract case class DownUnreachable(nodeGroup: SortedSet[UnreachableNode]) extends StrategyDecision
object DownUnreachable {
  def apply(worldView: WorldView): StrategyDecision = new DownUnreachable(worldView.unreachableNodes) {}
}

/**
 * Nothing has to be done. The cluster
 * is in a correct state.
 */
final case object Idle extends StrategyDecision
