package com.swissborg.sbr.strategy.downall

import cats.Id
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios._
import com.swissborg.sbr.strategy.StrategyDecision._

class DownAllSpec extends SBSpec {
  private val downAll: DownAll[Id] = new DownAll[Id]

  "DownAll" must {
    "always down nodes" in {
      forAll { worldView: WorldView =>
        downAll.takeDecision(worldView).map {
          case DownThese(DownSelf(_), DownReachable(_)) => succeed
          case _                                        => fail
        }
      }
    }

    simulate[Id, DownAll, SymmetricSplitScenario]("handle symmetric split scenarios")(identity)

    simulate[Id, DownAll, UpDisseminationScenario](
      "handle a split during up-dissemination scenarios")(identity)

    simulate[Id, DownAll, OldestRemovedScenario](
      "handle a split during the oldest-removed scenarios")(identity)
  }
}
