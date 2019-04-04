package akka.cluster.sbr

import cats.Monoid
import monocle.Getter

import scala.collection.immutable.SortedSet

/**
 * Represents the strategy that needs to be taken
 * to resolve a potential split-brain issue.
 */
sealed abstract class StrategyDecision extends Product with Serializable

object StrategyDecision {

  val nodesToDown: Getter[StrategyDecision, SortedSet[Node]] = Getter[StrategyDecision, SortedSet[Node]] {
    case DownThese(decision1, decision2)   => nodesToDown.get(decision1) ++ nodesToDown.get(decision2)
    case DownSelf(node)                    => SortedSet(node)
    case DownReachable(reachableNodes)     => reachableNodes.map(identity[Node])
    case DownUnreachable(unreachableNodes) => unreachableNodes.map(identity[Node])
    case _: Idle.type                      => SortedSet.empty
  }

  implicit class DecisionOps(private val decision: StrategyDecision) extends AnyVal {
    def nodesToDown: SortedSet[Node] = StrategyDecision.nodesToDown.get(decision)
  }

  implicit val strategyDecisionMonoid: Monoid[StrategyDecision] = new Monoid[StrategyDecision] {
    override def empty: StrategyDecision = Idle

    override def combine(x: StrategyDecision, y: StrategyDecision): StrategyDecision =
      (x, y) match {
        case (Idle, y)        => y
        case (x, Idle)        => x
        case (x: DownSelf, _) => x
        case (_, y: DownSelf) => y
        case (x, y)           => DownThese(x, y)
      }
  }
}

/**
 * The reachable nodes should be downed.
 */
sealed abstract case class DownReachable(nodeGroup: SortedSet[ReachableNode]) extends StrategyDecision
object DownReachable {
  def apply(worldView: WorldView): DownReachable =
    new DownReachable(worldView.reachableNodes) {}
}

sealed abstract case class DownSelf(node: Node) extends StrategyDecision
object DownSelf {
  def apply(worldView: WorldView): DownSelf = new DownSelf(worldView.selfNode) {}
}

/**
 * The unreachable nodes should be downed.
 */
sealed abstract case class DownUnreachable(nodeGroup: SortedSet[UnreachableNode]) extends StrategyDecision
object DownUnreachable {
  def apply(worldView: WorldView): DownUnreachable = new DownUnreachable(worldView.unreachableNodes) {}
}

final case class DownThese(decision1: StrategyDecision, decision2: StrategyDecision) extends StrategyDecision

/**
 * Nothing has to be done. The cluster
 * is in a correct state.
 */
final case object Idle extends StrategyDecision
