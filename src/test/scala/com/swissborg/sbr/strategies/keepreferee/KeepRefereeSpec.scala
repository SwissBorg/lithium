package com.swissborg.sbr.strategies.keepreferee

import cats.Id
import com.swissborg.sbr.scenarios._
import com.swissborg.sbr.{SBSpec, Simulation}

class KeepRefereeSpec extends SBSpec {
  "KeepReferee" must {
    "handle symmetric split scenarios" in {
      forAll { simulation: Simulation[Id, KeepReferee, SymmetricSplitScenario] =>
        simulation.splitBrainResolved shouldBe true
      }
    }

    "handle split during up-dissemination" in {
      forAll { simulation: Simulation[Id, KeepReferee, UpDisseminationScenario] =>
        simulation.splitBrainResolved shouldBe true
      }
    }

    "handle a split during the oldest-removed scenarios" in {
      forAll { simulation: Simulation[Id, KeepReferee, OldestRemovedScenario] =>
        simulation.splitBrainResolved shouldBe true
      }
    }
  }
}
