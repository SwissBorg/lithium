package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._

class UnreachableNodeSpec extends SBSpec {
  "UnreachableNode" - {
    "1 - should not affect the order" in {
      forAll { unreachableNodes: List[UnreachableNode] =>
        unreachableNodes.sorted.map(_.member) should ===(unreachableNodes.map(_.member).sorted)
      }
    }
  }
}
