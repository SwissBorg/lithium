package com.swissborg.lithium

package strategy

import cats.effect.SyncIO

class StaticQuorumSpec extends LithiumSpec {
  "StaticQuorum" must {
    simulate[SyncIO, StaticQuorum, CleanPartitionScenario]("handle clean partitions")(
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

    simulate[SyncIO, StaticQuorum, RemovedDisseminationScenario](
      "handle split during removed-dissemination"
    )(
      _.unsafeRunSync()
    )

    simulateWithNonCleanPartitions[SyncIO, StaticQuorum, CleanPartitionScenario](
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

    simulateWithNonCleanPartitions[SyncIO, StaticQuorum, RemovedDisseminationScenario](
      "handle non-clean partitions during removed-dissemination scenarios"
    )(
      _.unsafeRunSync()
    )
  }
}
