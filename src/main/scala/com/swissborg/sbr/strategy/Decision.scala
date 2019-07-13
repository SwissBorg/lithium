package com.swissborg.sbr
package strategy

import cats._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import monocle.Getter

import scala.collection.immutable.SortedSet

/**
  * Represents the strategy that needs to be taken
  * to resolve a potential split-brain issue.
  */
private[sbr] sealed abstract class Decision extends Product with Serializable

private[sbr] object Decision {

  /**
    * Decision to down the reachable nodes.
    */
  sealed abstract case class DownReachable(nodesToDown: SortedSet[ReachableNode]) extends Decision

  object DownReachable {
    def apply(worldView: WorldView): DownReachable =
      new DownReachable(worldView.reachableNodes) {}
  }

  sealed abstract case class DownIndirectlyConnected(
      nodesToDown: SortedSet[IndirectlyConnectedNode]
  ) extends Decision

  object DownIndirectlyConnected {
    def apply(worldView: WorldView): DownIndirectlyConnected =
      new DownIndirectlyConnected(worldView.indirectlyConnectedNodes) {}
  }

  /**
    * The unreachable nodes that should be downed.
    */
  sealed abstract case class DownUnreachable(nodesToDown: SortedSet[UnreachableNode])
      extends Decision

  object DownUnreachable {
    def apply(worldView: WorldView): DownUnreachable =
      new DownUnreachable(worldView.unreachableNodes) {}
  }

  final case class DownThese(decision1: Decision, decision2: Decision) extends Decision

  case object Idle extends Decision

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
  val nodesToDown: Getter[Decision, SortedSet[Node]] =
    Getter[Decision, SortedSet[Node]] {
      case DownThese(decision1, decision2)      => decision1.nodesToDown ++ decision2.nodesToDown
      case DownReachable(nodesToDown)           => nodesToDown.map(identity[Node])
      case DownUnreachable(nodesToDown)         => nodesToDown.map(identity[Node])
      case DownIndirectlyConnected(nodesToDown) => nodesToDown.map(identity[Node])
      case _: Idle.type                         => SortedSet.empty
    }

  implicit class DecisionOps(private val decision: Decision) extends AnyVal {
    def nodesToDown: SortedSet[Node] = Decision.nodesToDown.get(decision)

    /**
      * The strategy decision with the leafs without nodes to to
      * down recursively replaced by idle.
      */
    def simplify: Decision =
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

  def downReachable(worldView: WorldView): Decision = DownReachable(worldView)

  def downUnreachable(worldView: WorldView): Decision = DownUnreachable(worldView)

  def downThese(decision1: Decision, decision2: Decision): Decision =
    DownThese(decision1, decision2)

  def downIndirectlyConnected(worldView: WorldView): Decision =
    DownIndirectlyConnected(worldView)

  /**
    * Monoid combining decisions by yielding new one that is a union of both.
    */
  implicit val strategyDecisionMonoid: Monoid[Decision] = new Monoid[Decision] {
    override def empty: Decision = Idle

    override def combine(x: Decision, y: Decision): Decision =
      (x, y) match {
        case (Idle, y) => y
        case (x, Idle) => x
        case (x, y)    => DownThese(x, y)
      }
  }
}
