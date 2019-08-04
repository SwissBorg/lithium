package com.swissborg.lithium

package strategy

import cats.implicits._

import scala.util.Try

class KeepMajoritySpec extends LithiumSpec {
  "KeepMajority" must {
    simulate[Try, KeepMajority, CleanPartitionScenario]("handle clean partitions")(_.get)

    simulate[Try, KeepMajority, RemovedDisseminationScenario](
      "handle a split during removed-dissemination scenarios"
    )(_.get)

    simulate[Try, KeepMajority, OldestRemovedDisseminationScenario](
      "handle a split during oldest-removed scenarios"
    )(_.get)

    simulateWithNonCleanPartitions[Try, KeepMajority, CleanPartitionScenario](
      "handle non-clean partitions"
    )(
      _.get
    )

    simulateWithNonCleanPartitions[Try, KeepMajority, RemovedDisseminationScenario](
      "handle non-clean partitions during removed-dissemination scenarios"
    )(_.get)

    simulateWithNonCleanPartitions[Try, KeepMajority, OldestRemovedDisseminationScenario](
      "handle an unclean partition during oldest-removed scenarios"
    )(_.get)
  }
}
