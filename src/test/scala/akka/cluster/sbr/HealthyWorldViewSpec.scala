package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._

class HealthyWorldViewSpec extends SBSpec {
  "HealthyWorldView" must {
    "not have unreachable nodes" in {
      forAll { worldView: HealthyWorldView =>
        worldView.unreachableNodes shouldBe empty
      }
    }
  }
}
