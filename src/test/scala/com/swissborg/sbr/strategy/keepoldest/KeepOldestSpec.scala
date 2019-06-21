package com.swissborg.sbr.strategy.keepoldest

import cats.implicits._
import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.scenarios.{CleanPartitionsScenario, UpDisseminationScenario}

import scala.util.Try

class KeepOldestSpec extends SBSpec {
  "KeepOldest" must {
    simulate[Try, KeepOldest, CleanPartitionsScenario]("handle clean partitions")(_.get)

    simulate[Try, KeepOldest, UpDisseminationScenario]("handle split during up-dissemination")(
      _.get
    )

    simulateWithNonCleanPartitions[Try, KeepOldest, CleanPartitionsScenario](
      "handle non-clean partitions"
    )(
      _.get
    )

    simulateWithNonCleanPartitions[Try, KeepOldest, UpDisseminationScenario](
      "handle non-clean partitions during up-dissemination"
    )(_.get)
  }
}
