package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr._
import akka.cluster.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario}
import akka.cluster.sbr.strategies.keepmajority.ArbitraryInstances._
import akka.cluster.sbr.utils.PostResolution
import cats.implicits._

class KeepMajoritySpec extends SBSpec {
  "KeepMajority" - {
    "1 - should handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, keepMajority: KeepMajority) =>
        val remainingPartitions = scenario.worldViews.foldMap { worldView =>
          keepMajority.takeDecision(worldView).foldMap(PostResolution.fromDecision(worldView))
        }

        remainingPartitions.noSplitBrain shouldBe true
      }
    }

    "2 - should handle a split during the oldest-removed scenarios" in {
      forAll { (scenario: OldestRemovedScenario, keepMajority: KeepMajority) =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          keepMajority.takeDecision(worldView).foldMap(PostResolution.fromDecision(worldView))
        }

        remainingSubClusters.noSplitBrain shouldBe true
      }
    }
  }
}
