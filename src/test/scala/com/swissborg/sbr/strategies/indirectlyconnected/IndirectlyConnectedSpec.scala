package com.swissborg.sbr.strategies.indirectlyconnected

import cats.Id
import cats.implicits._
import com.swissborg.sbr._

class IndirectlyConnectedSpec extends SBSpec {
  "IndirectlyConnected" must {
    "down self if unreachable" in {
      forAll { worldView: WorldView =>
        IndirectlyConnected[Id]().takeDecision(worldView).map {
          case DownIndirectlyConnected(nodes) => worldView.indirectlyConnectedNodes should ===(nodes)
          case _                              => fail
        }
      }
    }
  }
}
