package com.swissborg.sbr.strategies.keepoldest

import cats.implicits._
import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.scenarios.{SymmetricSplitScenario, UpDisseminationScenario}
import com.swissborg.sbr.utils.PostResolution

class KeepOldestSpec extends SBSpec {
  "KeepOldest" must {
    "handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, downIfAlone: Boolean, role: String) =>
        val remainingPartitions = scenario.worldViews.foldMap { worldView =>
          KeepOldest(downIfAlone, role)
            .takeDecision(worldView)
            .foldMap(PostResolution.fromDecision(worldView))
        }

        remainingPartitions.noSplitBrain shouldBe true
      }
    }

    "handle split during up-dissemination" in {
      forAll { (scenario: UpDisseminationScenario, downIfAlone: Boolean, role: String) =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          KeepOldest(downIfAlone, role)
            .takeDecision(worldView)
            .foldMap(PostResolution.fromDecision(worldView))
        }

        remainingSubClusters.noSplitBrain shouldBe true
      }
    }
  }
}
