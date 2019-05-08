package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.implicits._
import cats.implicits._
import cats.Monoid

import scala.collection.immutable.SortedSet

class StrategyDecisionSpec extends SBSpec {
  "StrategyDecision" must {
    "extract the correct nodes from the world view" in {
      forAll { worldView: WorldView =>
        DownReachable(worldView).nodesToDown.map(_.member) should ===(worldView.reachableNodes.map(_.member))
        DownSelf(worldView).nodesToDown should ===(SortedSet(worldView.selfNode))
        DownUnreachable(worldView).nodesToDown.map(_.member) should ===(worldView.unreachableNodes.map(_.member))
      }
    }

    "extract the correct nodes from the decision" in {
      forAll { strategyDecision: StrategyDecision =>
        strategyDecision match {
          case DownReachable(nodeGroup) =>
            strategyDecision.nodesToDown.map(_.member) should ===(nodeGroup.map(_.member))

          case DownSelf(node) => strategyDecision.nodesToDown.map(_.member) should ===(SortedSet(node.member))

          case DownUnreachable(nodeGroup) =>
            strategyDecision.nodesToDown.map(_.member) should ===(nodeGroup.map(_.member))

          case DownThese(decision1, decision2) =>
            strategyDecision.nodesToDown should ===(decision1.nodesToDown ++ decision2.nodesToDown)

          case DownIndirectlyConnected(nodeGroup) =>
            strategyDecision.nodesToDown.map(_.member) should ===(nodeGroup.map(_.member))

          case Idle => strategyDecision.nodesToDown.isEmpty shouldBe true
        }
      }
    }

    "3 - correctly combine decisions" in {
      forAll { decisions: List[StrategyDecision] =>
        (decisions.flatMap(_.nodesToDown).toSet should contain).theSameElementsAs(
          decisions.foldRight(Monoid[StrategyDecision].empty)(Monoid[StrategyDecision].combine).nodesToDown
        )
      }
    }
  }
}
