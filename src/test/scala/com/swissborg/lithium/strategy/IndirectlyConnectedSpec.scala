package com.swissborg.lithium

package strategy

import cats.Id
import cats.implicits._

class IndirectlyConnectedSpec extends SBSpec {
  private val indirectlyConnected: IndirectlyConnected[Id] = new strategy.IndirectlyConnected[Id]

  "IndirectlyConnected" must {
    "down all the indirectly-connected nodes" in {
      forAll { worldView: WorldView =>
        indirectlyConnected.takeDecision(worldView).map {
          case Decision.DownIndirectlyConnected(nodes) =>
            worldView.indirectlyConnectedNodes should ===(nodes)

          case _ => fail
        }
      }
    }
  }
}
