package com.swissborg.sbr
package strategy

import cats.implicits._

import scala.util.Try

class KeepMajoritySpec extends SBSpec {
  "KeepMajority" must {
    simulate[Try, KeepMajority, CleanPartitionsScenario]("handle clean partitions")(_.get)

    simulateWithNonCleanPartitions[Try, KeepMajority, CleanPartitionsScenario](
      "handle non-clean partitions"
    )(
      _.get
    )
  }
}
