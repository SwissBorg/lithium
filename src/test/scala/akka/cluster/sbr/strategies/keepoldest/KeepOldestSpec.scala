package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.sbr.{MySpec, Strategy}
import akka.cluster.sbr.Scenario.SymmetricSplitScenario
import akka.cluster.sbr.strategies.keepoldest.KeepOldest.Config
import akka.cluster.sbr.utils.RemainingPartitions
import cats.implicits._
import akka.cluster.sbr.strategies.keepoldest.ArbitraryInstances._

class KeepOldestSpec extends MySpec {
  "KeepOldest" - {
    "1 - should handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, config: Config) =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          Strategy[KeepOldest](worldView, config).foldMap(RemainingPartitions.fromDecision)
        }

        remainingSubClusters.n.value should be <= 1
      }
    }
  }
}
