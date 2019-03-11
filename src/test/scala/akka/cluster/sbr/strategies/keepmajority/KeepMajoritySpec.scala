package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr._
import akka.cluster.sbr.Scenario.SymmetricSplitScenario
import akka.cluster.sbr.utils.RemainingSubClusters
import cats.implicits._

class KeepMajoritySpec extends MySpec {
  "KeepMajority" - {
    "1 - should handle symmetric split scenarios" in {
      forAll { scenario: SymmetricSplitScenario =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          Strategy[KeepMajority](worldView, ()).foldMap(RemainingSubClusters.fromDecision)
        }

        remainingSubClusters.n.value should be <= 1
      }
    }
  }
}
