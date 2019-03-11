package akka.cluster.sbr

import akka.actor.Address
import cats.data.NonEmptySet
import monocle.Getter

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
    case DownReachable(reachableNodes)       => reachableNodes.toSortedSet.map(_.node.address)
    case DownUnreachable(unreachableNodes)   => unreachableNodes.toSortedSet.map(_.node.address)
    case UnsafeDownReachable(reachableNodes) => reachableNodes.toSortedSet.map(_.node.address)
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
final case class DownReachable(nodeGroup: NonEmptySet[ReachableNode]) extends StrategyDecision

/**
 * TODO doc!
 */
final case class UnsafeDownReachable(nodeGroup: NonEmptySet[ReachableNode]) extends StrategyDecision

/**
 * The unreachable nodes should be downed.
 */
final case class DownUnreachable(nodeGroup: NonEmptySet[UnreachableNode]) extends StrategyDecision

/**
 * Nothing has to be done. The cluster
 * is in a correct state.
 */
final case object Idle extends StrategyDecision
