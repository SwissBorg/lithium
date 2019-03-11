package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr.Scenario.SymmetricSplitScenario
import akka.cluster.sbr.strategies.keepreferee.ArbitraryInstances._
import akka.cluster.sbr.strategies.keepreferee.KeepReferee.Config
import akka.cluster.sbr.utils.RemainingSubClusters
import akka.cluster.sbr.{MySpec, Strategy}
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

class KeepRefereeSpec extends MySpec {
  "KeepReferee" - {
    "1 - should handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, downAllIfLessThanNodes: Int Refined Positive) =>
        // same referee for everyone
        val referee = scenario.worldViews.head.allNodes.take(1).head.address.toString

        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          Strategy[KeepReferee](worldView, Config(referee, downAllIfLessThanNodes))
            .foldMap(RemainingSubClusters.fromDecision)
        }

        remainingSubClusters.n.value should be <= 1
      }
    }
  }
}
