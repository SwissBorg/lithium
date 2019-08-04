package com.swissborg.lithium

package strategy

import cats.Id
import cats.implicits._

class DownAllSpec extends LithiumSpec {
  "DownAll" must {
    "always down nodes" in {
      val downAll: DownAll[Id] = new strategy.DownAll[Id]
      forAll { worldView: WorldView =>
        downAll.takeDecision(worldView).map {
          case _: Decision.DownReachable => succeed
          case _                         => fail
        }
      }
    }

    simulate[Id, DownAll, CleanPartitionScenario]("handle clean partitions")(identity)

    simulate[Id, DownAll, UpDisseminationScenario](
      "handle a split during up-dissemination scenarios"
    )(identity)

    simulate[Id, DownAll, OldestRemovedDisseminationScenario](
      "handle a split during the oldest-removed scenarios"
    )(identity)

    simulateWithNonCleanPartitions[Id, DownAll, CleanPartitionScenario](
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
