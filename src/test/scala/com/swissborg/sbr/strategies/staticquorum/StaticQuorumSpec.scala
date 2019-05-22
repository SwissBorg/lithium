package com.swissborg.sbr.strategies.staticquorum

import cats.effect.SyncIO
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario}

class StaticQuorumSpec extends SBSpec {

  "StaticQuorum" must {
    simulate[SyncIO, StaticQuorum, SymmetricSplitScenario](
      "handle symmetric split scenarios with a correctly defined quorum size"
    )(_.unsafeRunSync())

    simulate[SyncIO, StaticQuorum, OldestRemovedScenario]("handle a split during the oldest-removed scenarios")(
      _.unsafeRunSync()
    )
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
