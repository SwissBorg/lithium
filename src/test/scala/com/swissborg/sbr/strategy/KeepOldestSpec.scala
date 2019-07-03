package com.swissborg.sbr
package strategy

import cats.implicits._

import scala.util.Try

class KeepOldestSpec extends SBSpec {
  "KeepOldest" must {
    simulate[Try, KeepOldest, CleanPartitionsScenario]("handle clean partitions")(_.get)

    simulate[Try, KeepOldest, UpDisseminationScenario](
      "handle split during up-dissemination scenarios"
    )(
      _.get
    )

    simulate[Try, KeepOldest, OldestRemovedDisseminationScenario](
      "handle split during oldest-removed scenarios"
    )(_.get)

    simulate[Try, KeepOldest, RemovedDisseminationScenario](
      "handle split during removed-dissemination scenarios"
    )(_.get)

    simulateWithNonCleanPartitions[Try, KeepOldest, CleanPartitionsScenario](
      "handle non-clean partitions"
    )(
      _.get
    )

    simulateWithNonCleanPartitions[Try, KeepOldest, UpDisseminationScenario](
      "handle non-clean partitions during up-dissemination scenarios"
    )(_.get)

    simulateWithNonCleanPartitions[Try, KeepOldest, OldestRemovedDisseminationScenario](
      "handle non-clean partitions during oldest-removed scenarios"
    )(_.get)

    simulateWithNonCleanPartitions[Try, KeepOldest, RemovedDisseminationScenario](
      "handle non-clean partitions during removed-dissemination scenarios"
    )(_.get)
  }
}
