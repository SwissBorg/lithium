package com.swissborg.sbr.strategy

import cats.Monoid
import cats.data.NonEmptySet
import com.swissborg.sbr._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import monocle.Getter

import scala.collection.immutable.SortedSet

/**
  * Represents the strategy that needs to be taken
  * to resolve a potential split-brain issue.
  */
private[sbr] sealed abstract class StrategyDecision extends Product with Serializable

private[sbr] object StrategyDecision {

  /**
    * Decision to down the reachable nodes.
    *
    * The `nodesToDown` contains all the nodes in the cluster as all the
    * non-reachable nodes will have to be downed before the reachable
    * nodes can gracefully leave the cluster.
    */
  sealed abstract case class DownReachable(nodesToDown: NonEmptySet[Node]) extends StrategyDecision

  object DownReachable {
    def apply(worldView: WorldView): DownReachable =
      new DownReachable(worldView.nodes) {}
  }

  sealed abstract case class DownIndirectlyConnected(
      nodesToDown: SortedSet[IndirectlyConnectedNode]
  ) extends StrategyDecision

  object DownIndirectlyConnected {
    def apply(worldView: WorldView): DownIndirectlyConnected =
      new DownIndirectlyConnected(worldView.indirectlyConnectedNodes) {}
  }

  /**
    * The unreachable nodes that should be downed.
    */
  sealed abstract case class DownUnreachable(nodesToDown: SortedSet[UnreachableNode])
      extends StrategyDecision

  object DownUnreachable {
    def apply(worldView: WorldView): DownUnreachable =
      new DownUnreachable(worldView.unreachableNodes) {}
  }

  final case class DownThese(decision1: StrategyDecision, decision2: StrategyDecision)
      extends StrategyDecision

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
  val nodesToDown: Getter[StrategyDecision, SortedSet[Node]] =
    Getter[StrategyDecision, SortedSet[Node]] {
      case DownThese(decision1, decision2)      => decision1.nodesToDown ++ decision2.nodesToDown
      case DownReachable(nodesToDown)           => nodesToDown.toSortedSet
      case DownUnreachable(nodesToDown)         => nodesToDown.map(identity[Node])
      case DownIndirectlyConnected(nodesToDown) => nodesToDown.map(identity[Node])
      case _: Idle.type                         => SortedSet.empty
    }

  implicit class DecisionOps(private val decision: StrategyDecision) extends AnyVal {
    def nodesToDown: SortedSet[Node] = StrategyDecision.nodesToDown.get(decision)

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
