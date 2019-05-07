package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._

class ReachableNodeSpec extends SBSpec {
  "ReachableNode" must {
    "not affect the order" in {
      forAll { reachableNodes: List[ReachableNode] =>
        reachableNodes.sorted.map(_.member) should ===(reachableNodes.map(_.member).sorted)
      }
    }
  }
}
