package com.swissborg.sbr.strategies.keepoldest

import cats.implicits._
import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.scenarios.{SymmetricSplitScenario, UpDisseminationScenario}
import com.swissborg.sbr.strategies.keepoldest.KeepOldest.Config
import com.swissborg.sbr.utils.PostResolution

import scala.util.Try

class KeepOldestSpec extends SBSpec {
  "KeepOldest" must {
    "handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, downIfAlone: Boolean, role: String) =>
        val remainingPartitions = scenario.worldViews.foldMap { worldView =>
          KeepOldest[Try](Config(downIfAlone, role))
            .takeDecision(worldView)
            .map(PostResolution.fromDecision(worldView))
        }.get

        remainingPartitions.noSplitBrain shouldBe true
      }
    }

    "handle split during up-dissemination" in {
      forAll { (scenario: UpDisseminationScenario, downIfAlone: Boolean, role: String) =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          KeepOldest[Try](Config(downIfAlone, role))
            .takeDecision(worldView)
            .map(PostResolution.fromDecision(worldView))
        }.get

        remainingSubClusters.noSplitBrain shouldBe true
      }
    }
  }
}
