package akka.cluster.sbr.strategies.indirectlyconnected

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr._

class IndirectedSpec extends MySpec {
  "Indirected" - {
    "1 - should down self if unreachable" in {
      forAll { worldView: WorldView =>
        Indirected().takeDecision(worldView).map {
          case DownIndirectlyConnected(nodes) => worldView.indirectlyConnectedNodes should ===(nodes)
          case _                              => fail
        }
      }
    }

//    "2 - should handle symmetric split scenarios" in {
//      forAll { scenario: SymmetricSplitScenario =>
//        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
//          Indirected.takeDecision(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
//        }
//
//        remainingSubClusters.n.value should be <= 1
//      }
//    }
//
//    "3 - should handle a split during up-dissemination scenarios" in {
//      forAll { scenario: UpDisseminationScenario =>
//        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
//          Indirected.takeDecision(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
//        }
//
//        remainingSubClusters.n.value should be <= 1
//      }
//    }
//
//    "4 - should handle a split during the oldest-removed scenarios" in {
//      forAll { scenario: OldestRemovedScenario =>
//        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
//          Indirected.takeDecision(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
//        }
//
//        remainingSubClusters.n.value should be <= 1
//      }
//    }
  }
}
