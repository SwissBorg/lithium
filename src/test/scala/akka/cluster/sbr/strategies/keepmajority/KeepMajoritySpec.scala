package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.Strategy.StrategyOps
import akka.cluster.sbr._
import akka.cluster.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario}
import akka.cluster.sbr.strategies.keepmajority.ArbitraryInstances._
import akka.cluster.sbr.utils.RemainingPartitions

class KeepMajoritySpec extends MySpec {
  "KeepMajority" - {
    "1 - should handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, keepMajority: KeepMajority) =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          keepMajority.handle(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
        }

        remainingSubClusters.n.value should be <= 1
      }
    }

    "2 - should handle a split during the oldest-removed scenarios" in {
      forAll { (scenario: OldestRemovedScenario, keepMajority: KeepMajority) =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          keepMajority.handle(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
        }

        remainingSubClusters.n.value should be <= 1
      }
    }
  }
}
