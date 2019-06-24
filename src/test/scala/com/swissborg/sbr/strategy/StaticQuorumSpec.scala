package com.swissborg.sbr
package strategy

import cats.effect.SyncIO
import com.swissborg.sbr.scenarios._

class StaticQuorumSpec extends SBSpec {
  "StaticQuorum" must {
    simulate[SyncIO, StaticQuorum, CleanPartitionsScenario]("handle clean partitions")(
      _.unsafeRunSync()
    )

    simulate[SyncIO, StaticQuorum, OldestRemovedDisseminationScenario](
      "handle a split during the oldest-removed scenarios"
    )(
      _.unsafeRunSync()
    )

    simulate[SyncIO, StaticQuorum, UpDisseminationScenario]("handle split during up-dissemination")(
      _.unsafeRunSync()
    )

    simulateWithNonCleanPartitions[SyncIO, StaticQuorum, CleanPartitionsScenario](
      "handle non-clean partitions"
    )(
      _.unsafeRunSync()
    )

    simulateWithNonCleanPartitions[SyncIO, StaticQuorum, OldestRemovedDisseminationScenario](
      "handle non-clean partitions during the oldest-removed scenarios"
    )(
      _.unsafeRunSync()
    )

    simulateWithNonCleanPartitions[SyncIO, StaticQuorum, UpDisseminationScenario](
      "handle non-clean partitions during up-dissemination scenarios"
    )(
      _.unsafeRunSync()
    )
  }
}
