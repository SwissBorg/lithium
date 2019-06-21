package com.swissborg.sbr.strategy.keepreferee

import cats.Id
import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.scenarios._

class KeepRefereeSpec extends SBSpec {
  "KeepReferee" must {
    simulate[Id, KeepReferee, CleanPartitionsScenario]("handle clean partitions")(identity)

    simulate[Id, KeepReferee, UpDisseminationScenario]("handle split during up-dissemination")(
      identity
    )

    simulate[Id, KeepReferee, OldestRemovedDisseminationScenario](
      "handle a split during the oldest-removed scenarios"
    )(identity)

    simulateWithNonCleanPartitions[Id, KeepReferee, CleanPartitionsScenario](
      "handle non-clean partitions"
    )(
      identity
    )

    simulateWithNonCleanPartitions[Id, KeepReferee, UpDisseminationScenario](
      "handle non-clean partitions during up-dissemination scenarios"
    )(
      identity
    )

    simulateWithNonCleanPartitions[Id, KeepReferee, OldestRemovedDisseminationScenario](
      "handle non-clean partitions during oldest-removed scenarios"
    )(identity)
  }
}
