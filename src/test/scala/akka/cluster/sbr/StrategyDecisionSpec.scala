package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import cats.kernel.Monoid

import scala.collection.immutable.SortedSet

class StrategyDecisionSpec extends SBSpec {
  "StrategyDecision" - {
    "1 - extract the correct nodes from the world view" in {
      forAll { worldView: WorldView =>
        DownReachable(worldView).nodesToDown.map(_.member) should ===(worldView.reachableNodes.map(_.member))
        DownSelf(worldView).nodesToDown should ===(SortedSet(worldView.selfNode))
        DownUnreachable(worldView).nodesToDown.map(_.member) should ===(worldView.unreachableNodes.map(_.member))
      }
    }

    "2 - extract the correct nodes from the decision" in {
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
        val maybeDownSelf = decisions.find {
          case _: DownSelf => true
          case _           => false
        }

        maybeDownSelf match {
          case Some(DownSelf(node)) =>
            decisions
              .foldRight(Monoid[StrategyDecision].empty)(Monoid[StrategyDecision].combine)
              .nodesToDown should ===(SortedSet(node))
          case None =>
            (decisions.flatMap(_.nodesToDown).toSet should contain).theSameElementsAs(
              decisions.foldRight(Monoid[StrategyDecision].empty)(Monoid[StrategyDecision].combine).nodesToDown
            )

          case _ => fail
        }
      }
    }
  }
}
