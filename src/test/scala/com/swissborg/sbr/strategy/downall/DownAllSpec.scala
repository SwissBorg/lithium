package com.swissborg.sbr.strategy.downall

import cats.Id
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios._
import com.swissborg.sbr.strategy.StrategyDecision._

class DownAllSpec extends SBSpec {
  "DownAll" must {
    "always down nodes" in {
      val downAll: DownAll[Id] = new DownAll[Id]
      forAll { worldView: WorldView =>
        downAll.takeDecision(worldView).map {
          case _: DownReachable => succeed
          case _                => fail
        }
      }
    }

    simulate[Id, DownAll, CleanPartitionsScenario]("handle clean partitions")(identity)

    simulate[Id, DownAll, UpDisseminationScenario](
      "handle a split during up-dissemination scenarios"
    )(identity)

    simulate[Id, DownAll, OldestRemovedDisseminationScenario](
      "handle a split during the oldest-removed scenarios"
    )(identity)

    simulateWithNonCleanPartitions[Id, DownAll, CleanPartitionsScenario](
      "handle non-clean partitions"
    )(
      identity
    )

    simulateWithNonCleanPartitions[Id, DownAll, UpDisseminationScenario](
      "handle non-clean partitions during up-dissemination scenarios"
    )(
      identity
    )

    simulateWithNonCleanPartitions[Id, DownAll, OldestRemovedDisseminationScenario](
      "handle non-clean partitions during oldest-removed scenarios"
    )(
      identity
    )
  }
}
