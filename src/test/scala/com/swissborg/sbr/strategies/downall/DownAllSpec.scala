package com.swissborg.sbr.strategies.downall

import cats.Id
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario, UpDisseminationScenario}
import com.swissborg.sbr.utils.PostResolution

class DownAllSpec extends SBSpec {
  private val downAll: DownAll[Id] = new DownAll[Id]

  "DownAll" must {
    "always down nodes" in {
      forAll { worldView: WorldView =>
        downAll.takeDecision(worldView).map {
          case DownThese(DownSelf(_), DownReachable(_)) => succeed
          case _                                        => fail
        }
      }
    }

    "handle symmetric split scenarios" in {
      forAll { scenario: SymmetricSplitScenario =>
        val remainingPartitions = scenario.worldViews
          .foldMap { worldView =>
            downAll.takeDecision(worldView).map(PostResolution.fromDecision(worldView))
          }

        remainingPartitions.noSplitBrain shouldBe true
      }
    }

    "handle a split during up-dissemination scenarios" in {
      forAll { scenario: UpDisseminationScenario =>
        val remainingPartitions = scenario.worldViews
          .foldMap { worldView =>
            downAll.takeDecision(worldView).map(PostResolution.fromDecision(worldView))
          }

        remainingPartitions.noSplitBrain shouldBe true
      }
    }

    "handle a split during the oldest-removed scenarios" in {
      forAll { scenario: OldestRemovedScenario =>
        val remainingPartitions = scenario.worldViews
          .foldMap { worldView =>
            downAll.takeDecision(worldView).map(PostResolution.fromDecision(worldView))
          }

        remainingPartitions.noSplitBrain shouldBe true
      }
    }
  }
}
