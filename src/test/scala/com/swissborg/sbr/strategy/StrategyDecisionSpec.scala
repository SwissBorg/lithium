package com.swissborg.sbr.strategy

import cats.Monoid
import cats.implicits._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.strategy.StrategyDecision._
import com.swissborg.sbr.{Node, SBSpec, WorldView}

import scala.collection.immutable.SortedSet

class StrategyDecisionSpec extends SBSpec {
  "StrategyDecision" must {
    "extract the correct nodes from the world view" in {
      forAll { worldView: WorldView =>
        DownReachable(worldView).nodesToDown.map(_.member) should ===(
          worldView.nodes.map(_.member)
        )

        DownUnreachable(worldView).nodesToDown.map(_.member) should ===(
          worldView.unreachableNodes.map(_.member)
        )
      }
    }

    "extract the correct nodes from the decision" in {
      forAll { strategyDecision: StrategyDecision =>
        strategyDecision match {
          case DownReachable(nodesToDown) =>
            strategyDecision.nodesToDown.map(_.member) should ===(
              nodesToDown.map(_.member).toSortedSet
            )

          case DownUnreachable(nodesToDown) =>
            strategyDecision.nodesToDown.map(_.member) should ===(nodesToDown.map(_.member))

          case DownThese(decision1, decision2) =>
            strategyDecision.nodesToDown should ===(decision1.nodesToDown ++ decision2.nodesToDown)

          case DownIndirectlyConnected(nodesToDown) =>
            strategyDecision.nodesToDown.map(_.member) should ===(nodesToDown.map(_.member))

          case Idle => strategyDecision.nodesToDown.isEmpty shouldBe true
        }
      }
    }

    "correctly combine decisions" in {
      forAll { decisions: List[StrategyDecision] =>
        val expectedNodesToDown: SortedSet[Node] =
          decisions.flatMap(_.nodesToDown)(collection.breakOut)

        expectedNodesToDown should contain theSameElementsAs
          decisions
            .foldRight(Monoid[StrategyDecision].empty)(Monoid[StrategyDecision].combine)
            .nodesToDown

      }
    }
  }
}
