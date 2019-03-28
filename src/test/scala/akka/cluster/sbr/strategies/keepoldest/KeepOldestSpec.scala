package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.sbr.MySpec
import akka.cluster.sbr.strategy.ops._
import akka.cluster.sbr.scenarios.{SymmetricSplitScenario, UpDisseminationScenario}
import akka.cluster.sbr.strategies.keepoldest.ArbitraryInstances._
import akka.cluster.sbr.utils.RemainingPartitions

class KeepOldestSpec extends MySpec {
  "KeepOldest" - {
    "1 - should handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, keepOldest: KeepOldest) =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          keepOldest.handle(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
        }

        remainingSubClusters.n.value should be <= 1
      }
    }

    "2 - should handle split during up-dissemination" in {
      forAll { (scenario: UpDisseminationScenario, keepOldest: KeepOldest) =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          keepOldest.handle(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
        }

        remainingSubClusters.n.value should be <= 1
      }
    }
  }
}
