package com.swissborg.sbr.strategies.keepmajority

import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario}
import com.swissborg.sbr.strategies.keepmajority.ArbitraryInstances._
import com.swissborg.sbr.utils.PostResolution

class KeepMajoritySpec extends SBSpec {
  "KeepMajority" must {
    "handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, keepMajority: KeepMajority) =>
        val remainingPartitions = scenario.worldViews
          .foldMap { worldView =>
            keepMajority.takeDecision(worldView).map(PostResolution.fromDecision(worldView))
          }
          .unsafeRunSync()

        remainingPartitions.noSplitBrain shouldBe true
      }
    }

    "handle a split during the oldest-removed scenarios" in {
      forAll { (scenario: OldestRemovedScenario, keepMajority: KeepMajority) =>
        val remainingSubClusters = scenario.worldViews
          .foldMap { worldView =>
            keepMajority.takeDecision(worldView).map(PostResolution.fromDecision(worldView))
          }
          .unsafeRunSync()

        remainingSubClusters.noSplitBrain shouldBe true
      }
    }
  }
}
