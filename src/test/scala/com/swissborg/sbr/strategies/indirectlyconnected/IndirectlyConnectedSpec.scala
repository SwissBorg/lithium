package com.swissborg.sbr.strategies.indirectlyconnected

import cats.Id
import cats.implicits._
import com.swissborg.sbr._

class IndirectlyConnectedSpec extends SBSpec {
  private val indirectlyConnected: IndirectlyConnected[Id] = new IndirectlyConnected[Id]

  "IndirectlyConnected" must {
    "down self if unreachable" in {
      forAll { worldView: WorldView =>
        indirectlyConnected.takeDecision(worldView).map {
          case DownIndirectlyConnected(nodes) => worldView.indirectlyConnectedNodes should ===(nodes)
          case _                              => fail
        }
      }
    }
  }
}
