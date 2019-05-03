package akka.cluster.sbr.strategies.downall

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr._
import akka.cluster.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario, UpDisseminationScenario}
import akka.cluster.sbr.strategy.ops._
import akka.cluster.sbr.utils.PostResolution
import cats.implicits._

class DownAllSpec extends MySpec {
  "DownAll" - {
    "1 - should always down nodes" in {
      forAll { worldView: WorldView =>
        DownAll.takeDecision(worldView).map {
          case DownThese(DownSelf(_), DownReachable(_)) => succeed
          case _                                        => fail
        }
      }
    }

    "2 - should handle symmetric split scenarios" in {
      forAll { scenario: SymmetricSplitScenario =>
        val remainingPartitions = scenario.worldViews.foldMap { worldView =>
          DownAll.takeDecision(worldView).foldMap(PostResolution.fromDecision(worldView))
        }

        remainingPartitions.noSplitBrain shouldBe true
      }
    }

    "3 - should handle a split during up-dissemination scenarios" in {
      forAll { scenario: UpDisseminationScenario =>
        val remainingPartitions = scenario.worldViews.foldMap { worldView =>
          DownAll.takeDecision(worldView).foldMap(PostResolution.fromDecision(worldView))
        }

        remainingPartitions.noSplitBrain shouldBe true
      }
    }

    "4 - should handle a split during the oldest-removed scenarios" in {
      forAll { scenario: OldestRemovedScenario =>
        val remainingPartitions = scenario.worldViews.foldMap { worldView =>
          DownAll.takeDecision(worldView).foldMap(PostResolution.fromDecision(worldView))
        }

        remainingPartitions.noSplitBrain shouldBe true
      }
    }
  }
}
