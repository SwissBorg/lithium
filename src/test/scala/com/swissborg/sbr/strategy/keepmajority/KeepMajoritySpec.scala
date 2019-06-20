package com.swissborg.sbr.strategy.keepmajority

import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios._

import scala.util.Try

class KeepMajoritySpec extends SBSpec {
  "KeepMajority" must {
    simulate[Try, KeepMajority, CleanPartitionsScenario]("handle clean partitions")(_.get)

    simulate[Try, KeepMajority, OldestRemovedDisseminationScenario](
      "handle a split during the oldest-removed scenarios"
    )(_.get)

    simulateWithNonCleanPartitions[Try, KeepMajority, CleanPartitionsScenario](
      "handle non-clean partitions"
    )(
      _.get
    )

    simulateWithNonCleanPartitions[Try, KeepMajority, OldestRemovedDisseminationScenario](
      "handle non-clean partitions during the oldest-removed scenarios..."
    )(_.get)
  }
}
