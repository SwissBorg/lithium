package com.swissborg.sbr.strategies.staticquorum

import cats.effect.SyncIO
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario}

class StaticQuorumSpec extends SBSpec {

  "StaticQuorum" must {
    "handle symmetric split scenarios with a correctly defined quorum size" in {
      forAll { simulation: Simulation[SyncIO, StaticQuorum, SymmetricSplitScenario] =>
        simulation.splitBrainResolved.unsafeRunSync() shouldBe true
      }
    }

    "handle a split during the oldest-removed scenarios" in {
      forAll { simulation: Simulation[SyncIO, StaticQuorum, OldestRemovedScenario] =>
        simulation.splitBrainResolved.unsafeRunSync() shouldBe true
      }
    }
    // TODO check if can really be handled
    //    "2 - should handle split during up-dissemination" in {
    //      forAll { scenario: UpDisseminationScenario =>
    //        implicit val _: Arbitrary[Config] = com.swissborg.sbr.strategies.staticquorum.StaticQuorumSpec.arbConfig(scenario.clusterSize)
    //
    //        forAll { config: Config =>
    //          val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
    //            Strategy[StaticQuorum](worldView, config).foldMap(RemainingPartitions.fromDecision)
    //          }
    //
    //          remainingSubClusters.n.value should be <= 1
    //        }
    //      }
    //    }
  }
}
