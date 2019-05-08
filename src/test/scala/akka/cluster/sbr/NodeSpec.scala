package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._

class NodeSpec extends SBSpec {
  "Node" must {
    "UnreachableNode should not affect the order" in {
      forAll { nodes: List[UnreachableNode] =>
        nodes.sorted.map(_.member) should ===(nodes.map(_.member).sorted)
      }
    }

    "ReachableNode should not affect the order" in {
      forAll { nodes: List[ReachableNode] =>
        nodes.sorted.map(_.member) should ===(nodes.map(_.member).sorted)
      }
    }
  }
}
