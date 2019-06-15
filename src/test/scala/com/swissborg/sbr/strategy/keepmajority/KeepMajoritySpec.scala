package com.swissborg.sbr.strategy.keepmajority

import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario}

import scala.util.Try

class KeepMajoritySpec extends SBSpec {
  "KeepMajority" must {
    simulate[Try, KeepMajority, SymmetricSplitScenario]("handle symmetric split scenarios")(_.get)

    simulate[Try, KeepMajority, OldestRemovedScenario](
      "handle a split during the oldest-removed scenarios")(_.get)
  }
}
