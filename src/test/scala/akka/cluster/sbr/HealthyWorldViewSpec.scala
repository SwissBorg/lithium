package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._

class HealthyWorldViewSpec extends MySpec {
  "HealthyWorldView" - {
    "1 - should not have unreachable nodes" in {
      forAll { worldView: HealthyWorldView =>
        worldView.unreachableNodes shouldBe empty
      }
    }

//    "2 - should have at least a considered reachable node" in {
//      forAll { worldView: HealthyWorldView =>
//        worldView.consideredReachableNodes shouldBe 'nonEmpty
//      }
//    }
  }
}
