package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.sbr.MySpec
import akka.cluster.sbr.scenarios.{SymmetricSplitScenario, UpDisseminationScenario}
import akka.cluster.sbr.strategies.keepoldest.ArbitraryInstances._
import akka.cluster.sbr.strategy.ops._
import akka.cluster.sbr.utils.PostResolution
import cats.implicits._

class KeepOldestSpec extends MySpec {
  "KeepOldest" - {
    "1 - should handle symmetric split scenarios" in {
      forAll { (scenario: SymmetricSplitScenario, keepOldest: KeepOldest) =>
        val remainingPartitions = scenario.worldViews.foldMap { worldView =>
          keepOldest.takeDecision(worldView).foldMap(PostResolution.fromDecision(worldView))
        }

//        println(s"scenario = ${scenario.worldViews.map(_.nodes)}")
//        println(s"remainingPartitions = $remainingPartitions")
        remainingPartitions.noSplitBrain shouldBe true
      }
    }

    "2 - should handle split during up-dissemination" in {
      forAll { (scenario: UpDisseminationScenario, keepOldest: KeepOldest) =>
        val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
          keepOldest.takeDecision(worldView).foldMap(PostResolution.fromDecision(worldView))
        }

        remainingSubClusters.noSplitBrain shouldBe true
      }
    }
  }
}
