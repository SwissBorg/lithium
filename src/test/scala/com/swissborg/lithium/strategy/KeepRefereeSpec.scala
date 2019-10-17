package com.swissborg.lithium

package strategy

import cats.Id

class KeepRefereeSpec extends LithiumSpec {
  "KeepReferee" must {
    simulate[Id, KeepReferee, CleanPartitionScenario]("handle clean partitions")(identity)

    simulate[Id, KeepReferee, UpDisseminationScenario]("handle split during up-dissemination")(identity)

    simulate[Id, KeepReferee, RemovedDisseminationScenario](
      "handle a split during the removed-dissemination scenarios"
    )(identity)

    simulate[Id, KeepReferee, OldestRemovedDisseminationScenario]("handle a split during the oldest-removed scenarios")(
      identity
    )

    simulateWithNonCleanPartitions[Id, KeepReferee, CleanPartitionScenario]("handle non-clean partitions")(identity)

    simulateWithNonCleanPartitions[Id, KeepReferee, UpDisseminationScenario](
      "handle non-clean partitions during up-dissemination scenarios"
    )(identity)

    simulateWithNonCleanPartitions[Id, KeepReferee, OldestRemovedDisseminationScenario](
      "handle non-clean partitions during oldest-removed scenarios"
    )(identity)

    simulateWithNonCleanPartitions[Id, KeepReferee, RemovedDisseminationScenario](
      "handle a non-clean partition during the removed-dissemination scenarios"
    )(identity)
  }
}
