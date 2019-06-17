package com.swissborg.sbr.strategy

import cats.Monoid
import com.swissborg.sbr._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import monocle.Getter

/**
  * Represents the strategy that needs to be taken
  * to resolve a potential split-brain issue.
  */
private[sbr] sealed abstract class StrategyDecision extends Product with Serializable

private[sbr] object StrategyDecision {

  /**
    * The reachable nodes that should be downed.
    * Also downs the `selfNode` if it is indirectly-connected.
    */
  sealed abstract case class DownReachable(nodesToDown: Set[ReachableNode], selfNode: Node)
      extends StrategyDecision

  object DownReachable {
    def apply(worldView: WorldView): DownReachable =
      new DownReachable(worldView.reachableNodes, worldView.selfNode) {}
  }

  sealed abstract case class DownIndirectlyConnected(nodesToDown: Set[IndirectlyConnectedNode])
      extends StrategyDecision

  object DownIndirectlyConnected {
    def apply(worldView: WorldView): DownIndirectlyConnected =
      new DownIndirectlyConnected(worldView.indirectlyConnectedNodes) {}
  }

  /**
    * The unreachable nodes that should be downed.
    */
  sealed abstract case class DownUnreachable(nodesToDown: Set[UnreachableNode])
      extends StrategyDecision

  object DownUnreachable {
    def apply(worldView: WorldView): DownUnreachable =
      new DownUnreachable(worldView.unreachableNodes) {}
  }

  final case class DownThese(decision1: StrategyDecision, decision2: StrategyDecision)
      extends StrategyDecision

  /**
    * Nothing has to be done.
    */
  case object Idle extends StrategyDecision

  final case class SimpleStrategyDecision(
      downReachable: List[SimpleMember],
      downIndirectlyConnected: List[SimpleMember],
      downUnreachable: List[SimpleMember]
  )

  object SimpleStrategyDecision {
    val empty: SimpleStrategyDecision = SimpleStrategyDecision(List.empty, List.empty, List.empty)

    implicit val simpleStrategyDecisionEncoder: Encoder[SimpleStrategyDecision] = deriveEncoder
  }

  /**
    * Get the nodes to down given the strategy decision.
    */
  val nodesToDown: Getter[StrategyDecision, Set[Node]] = Getter[StrategyDecision, Set[Node]] {
    case DownThese(decision1, decision2)      => decision1.nodesToDown ++ decision2.nodesToDown
    case DownReachable(nodesToDown, selfNode) => nodesToDown.map(identity[Node]) + selfNode
    case DownUnreachable(nodesToDown)         => nodesToDown.map(identity[Node])
    case DownIndirectlyConnected(nodesToDown) => nodesToDown.map(identity[Node])
    case _: Idle.type                         => Set.empty
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
          case Idle | _: DownIndirectlyConnected | _: DownUnreachable | _: DownReachable =>
            decision

          case DownThese(decision1, decision2) =>
            if (decision1.nodesToDown.isEmpty) decision2.simplify
            else if (decision2.nodesToDown.isEmpty) decision1.simplify
            else decision
        }
      }

    def simple: SimpleStrategyDecision =
      nodesToDown.foldLeft(SimpleStrategyDecision.empty) {
        case (decision, node) =>
          node match {
            case ReachableNode(member) =>
              decision.copy(
                downReachable = SimpleMember.fromMember(member) :: decision.downReachable
              )

            case IndirectlyConnectedNode(member) =>
              decision.copy(
                downIndirectlyConnected = SimpleMember
                  .fromMember(member) :: decision.downIndirectlyConnected
              )

            case UnreachableNode(member) =>
              decision.copy(
                downUnreachable = SimpleMember.fromMember(member) :: decision.downUnreachable
              )
          }
      }
  }

  def downReachable(worldView: WorldView): StrategyDecision = DownReachable(worldView)

  def downUnreachable(worldView: WorldView): StrategyDecision = DownUnreachable(worldView)

  def downThese(decision1: StrategyDecision, decision2: StrategyDecision): StrategyDecision =
    DownThese(decision1, decision2)

  def downIndirectlyConnected(worldView: WorldView): StrategyDecision =
    DownIndirectlyConnected(worldView)

  val idle: StrategyDecision = Idle

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
