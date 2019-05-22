package com.swissborg.sbr.strategies.keepmajority

import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario}

import scala.util.Try

class KeepMajoritySpec extends SBSpec {
  "KeepMajority" must {
    "handle symmetric split scenarios" in {
      forAll { simulation: Simulation[Try, KeepMajority, SymmetricSplitScenario] =>
        simulation.splitBrainResolved.get shouldBe true
      }
    }

    "handle a split during the oldest-removed scenarios" in {
      forAll { simulation: Simulation[Try, KeepMajority, OldestRemovedScenario] =>
        simulation.splitBrainResolved.get shouldBe true
      }
    }
  }
}
