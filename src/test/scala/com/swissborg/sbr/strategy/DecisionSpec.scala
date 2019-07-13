package com.swissborg.sbr
package strategy

import cats.Monoid
import cats.implicits._

import scala.collection.immutable.SortedSet

class DecisionSpec extends SBSpec {
  "StrategyDecision" must {
    "extract the correct nodes from the world view" in {
      forAll { worldView: WorldView =>
        Decision.DownReachable(worldView).nodesToDown should ===(worldView.reachableNodes)
        Decision.DownUnreachable(worldView).nodesToDown should ===(worldView.unreachableNodes)

        Decision.DownIndirectlyConnected(worldView).nodesToDown should
          ===(worldView.indirectlyConnectedNodes)
      }
    }

    "extract the correct nodes from the decision" in {
      forAll { strategyDecision: Decision =>
        strategyDecision match {
          case Decision.DownReachable(nodesToDown) =>
            strategyDecision.nodesToDown should ===(nodesToDown.map(identity[Node]))

          case Decision.DownUnreachable(nodesToDown) =>
            strategyDecision.nodesToDown should ===(nodesToDown.map(identity[Node]))

          case Decision.DownThese(decision1, decision2) =>
            strategyDecision.nodesToDown should ===(decision1.nodesToDown ++ decision2.nodesToDown)

          case Decision.DownIndirectlyConnected(nodesToDown) =>
            strategyDecision.nodesToDown should ===(nodesToDown.map(identity[Node]))

          case Decision.Idle => strategyDecision.nodesToDown.isEmpty shouldBe true
        }
      }
    }

    "correctly combine decisions" in {
      forAll { decisions: List[Decision] =>
        val expectedNodesToDown: SortedSet[Node] =
          decisions.flatMap(_.nodesToDown)(collection.breakOut)

        expectedNodesToDown should contain theSameElementsAs
          decisions
            .foldRight(Monoid[Decision].empty)(Monoid[Decision].combine)
            .nodesToDown

      }
    }
  }
}
