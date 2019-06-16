package com.swissborg.sbr.strategy.keepoldest

import cats.implicits._
import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.scenarios.{SymmetricSplitScenario, UpDisseminationScenario}

import scala.util.Try

class KeepOldestSpec extends SBSpec {
  "KeepOldest" must {
    simulate[Try, KeepOldest, SymmetricSplitScenario]("handle symmetric split scenarios")(_.get)

    simulate[Try, KeepOldest, UpDisseminationScenario]("handle split during up-dissemination")(
      _.get
    )
  }
}
