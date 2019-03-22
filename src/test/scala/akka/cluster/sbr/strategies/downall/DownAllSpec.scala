package akka.cluster.sbr.strategies.downall

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.Strategy.StrategyOps
import akka.cluster.sbr._
import akka.cluster.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario, UpDisseminationScenario}
import akka.cluster.sbr.utils.RemainingPartitions

class DownAllSpec extends MySpec {
  "DownAll" - {
    "1 - should always down nodes" in {
      forAll { worldView: WorldView =>
        DownAll.handle(worldView).map {
          case DownReachable(_) => succeed
          case Idle             => succeed
          case _                => fail
        }
      }
    }

    "2 - should handle symmetric split scenarios" in {
      forAll { scenario: SymmetricSplitScenario =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          DownAll.handle(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
        }

        remainingSubClusters.n.value should be <= 1
      }
    }

    "3 - should handle a split during up-dissemination scenarios" in {
      forAll { scenario: UpDisseminationScenario =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          DownAll.handle(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
        }

        remainingSubClusters.n.value should be <= 1
      }
    }

    "4 - should handle a split during the oldest-removed scenarios" in {
      forAll { scenario: OldestRemovedScenario =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          DownAll.handle(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
        }

        remainingSubClusters.n.value should be <= 1
      }
    }
  }
}
