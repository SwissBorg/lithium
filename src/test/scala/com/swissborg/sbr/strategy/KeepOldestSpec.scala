package com.swissborg.sbr
package strategy

import cats.implicits._

import scala.util.Try

class KeepOldestSpec extends SBSpec {
  "KeepOldest" must {
//    simulate[Try, KeepOldest, CleanPartitionsScenario]("handle clean partitions")(_.get)

    simulate[Try, KeepOldest, UpDisseminationScenario](
      "handle split during up-dissemination scenarios"
    )(
      _.get
    )

    simulateWithNonCleanPartitions[Try, KeepOldest, CleanPartitionsScenario](
      "handle non-clean partitions"
    )(
      _.get
    )

    simulateWithNonCleanPartitions[Try, KeepOldest, UpDisseminationScenario](
      "handle non-clean partitions during up-dissemination scenarios"
    )(_.get)
  }
}
