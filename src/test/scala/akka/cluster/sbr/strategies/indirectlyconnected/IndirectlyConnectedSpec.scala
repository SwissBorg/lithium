package akka.cluster.sbr.strategies.indirectlyconnected

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr._

class IndirectlyConnectedSpec extends SBSpec {
  "IndirectlyConnected" must {
    "down self if unreachable" in {
      forAll { worldView: WorldView =>
        IndirectlyConnected().takeDecision(worldView).map {
          case DownIndirectlyConnected(nodes) => worldView.indirectlyConnectedNodes should ===(nodes)
          case _                              => fail
        }
      }
    }
  }
}
