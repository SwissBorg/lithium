package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario, UpDisseminationScenario}
import akka.cluster.sbr.strategies.keepreferee.KeepReferee.Config
import akka.cluster.sbr.utils.RemainingPartitions
import akka.cluster.sbr.{MySpec, Strategy}
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._

class KeepRefereeSpec extends MySpec {
  "KeepReferee" - {
    "1 - should handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, downAllIfLessThanNodes: Int Refined Positive) =>
        // same referee for everyone
        val referee = scenario.worldViews.head.allConsideredNodes.take(1).head.address.toString

        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          Strategy[KeepReferee](worldView, Config(referee, downAllIfLessThanNodes))
            .foldMap(RemainingPartitions.fromDecision(worldView))
        }

        remainingSubClusters.n.value should be <= 1
      }
    }

    "2 - should handle split during up-dissemination" in {
      forAll { (scenario: UpDisseminationScenario, downAllIfLessThanNodes: Int Refined Positive) =>
        // same referee for everyone
        val referee = scenario.worldViews.head.allConsideredNodes.take(1).head.address.toString

        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          Strategy[KeepReferee](worldView, Config(referee, downAllIfLessThanNodes))
            .foldMap(RemainingPartitions.fromDecision(worldView))
        }

        remainingSubClusters.n.value should be <= 1
      }
    }

    "3 - should handle a split during the oldest-removed scenarios" in {
      forAll { (scenario: OldestRemovedScenario, downAllIfLessThanNodes: Int Refined Positive) =>
        // same referee for everyone
        scenario.worldViews.head.allConsideredNodes
          .take(1)
          .headOption
          .map { referee =>
            val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
              Strategy[KeepReferee](worldView, Config(referee.address.toString, downAllIfLessThanNodes))
                .foldMap(RemainingPartitions.fromDecision(worldView))
            }

            remainingSubClusters.n.value should be <= 1
          }
          .getOrElse(succeed)
      }
    }
  }
}
