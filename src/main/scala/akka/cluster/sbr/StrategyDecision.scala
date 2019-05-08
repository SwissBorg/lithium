package akka.cluster.sbr

import cats.Monoid
import monocle.Getter

/**
 * Represents the strategy that needs to be taken
 * to resolve a potential split-brain issue.
 */
sealed abstract class StrategyDecision extends Product with Serializable

object StrategyDecision {

  /**
   * Get the nodes to down given the strategy decision.
   */
  val nodesToDown: Getter[StrategyDecision, Set[Node]] = Getter[StrategyDecision, Set[Node]] {
    case DownThese(decision1, decision2)                   => nodesToDown.get(decision1) ++ nodesToDown.get(decision2)
    case DownSelf(node)                                    => Set(node)
    case DownReachable(reachableNodes)                     => reachableNodes.map(identity[Node])
    case DownUnreachable(unreachableNodes)                 => unreachableNodes.map(identity[Node])
    case DownIndirectlyConnected(indirectlyConnectedNodes) => indirectlyConnectedNodes.map(identity[Node])
    case _: Idle.type                                      => Set.empty
  }

  implicit class DecisionOps(private val decision: StrategyDecision) extends AnyVal {
    def nodesToDown: Set[Node] = StrategyDecision.nodesToDown.get(decision)

    /**
     * The strategy decision with the leafs without nodes to to
     * down recursively replaced by idle.
     */
    def simplify: StrategyDecision =
      if (decision.nodesToDown.isEmpty) Idle
      else {
        decision match {
          case Idle | _: DownSelf | _: DownIndirectlyConnected | _: DownUnreachable | _: DownReachable => decision
          case DownThese(decision1, decision2) =>
            if (decision1.nodesToDown.isEmpty) decision2.simplify
            else if (decision2.nodesToDown.isEmpty) decision1.simplify
            else decision
        }
      }

  }

  /**
   * Monoid combining decisions by yielding new one that is a union of both.
   */
  implicit val strategyDecisionMonoid: Monoid[StrategyDecision] = new Monoid[StrategyDecision] {
    override def empty: StrategyDecision = Idle

    override def combine(x: StrategyDecision, y: StrategyDecision): StrategyDecision =
      (x, y) match {
        case (Idle, y) => y
        case (x, Idle) => x
        case (x, y)    => DownThese(x, y)
      }
  }
}

/**
 * The reachable nodes that should be downed.
 */
sealed abstract case class DownReachable(nodeGroup: Set[ReachableNode]) extends StrategyDecision

object DownReachable {
  def apply(worldView: WorldView): DownReachable =
    new DownReachable(worldView.reachableNodes) {}
}

sealed abstract case class DownSelf(node: Node) extends StrategyDecision

object DownSelf {
  def apply(worldView: WorldView): DownSelf = new DownSelf(worldView.selfNode) {}
}

sealed abstract case class DownIndirectlyConnected(nodeGroup: Set[IndirectlyConnectedNode]) extends StrategyDecision

object DownIndirectlyConnected {
  def apply(worldView: WorldView): DownIndirectlyConnected =
    new DownIndirectlyConnected(worldView.indirectlyConnectedNodes) {}
}

/**
 * The unreachable nodes that should be downed.
 */
sealed abstract case class DownUnreachable(nodeGroup: Set[UnreachableNode]) extends StrategyDecision

object DownUnreachable {
  def apply(worldView: WorldView): DownUnreachable = new DownUnreachable(worldView.unreachableNodes) {}
}

final case class DownThese(decision1: StrategyDecision, decision2: StrategyDecision) extends StrategyDecision

/**
 * Nothing has to be done.
 */
case object Idle extends StrategyDecision
