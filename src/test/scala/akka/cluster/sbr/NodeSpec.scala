package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.implicits._

class NodeSpec extends MySpec {
  "Node" - {
    "1 - ReachableConsideredNode should not affect the order" in {
      forAll { nodes: List[ReachableConsideredNode] =>
        nodes.sorted.map(_.member) should ===(nodes.map(_.member).sorted)
      }
    }

    "2 - UnreachableNode should not affect the order" in {
      forAll { nodes: List[UnreachableNode] =>
        nodes.sorted.map(_.member) should ===(nodes.map(_.member).sorted)
      }
    }

    "3 - ReachableNode should not affect the order" in {
      forAll { nodes: List[ReachableNode] =>
        nodes.sorted.map(_.member) should ===(nodes.map(_.member).sorted)
      }
    }

    "4 - IndirectlyConnectedNode should not affect the order" in {
      forAll { nodes: List[IndirectlyConnectedNode] =>
        nodes.sorted.map(_.member) should ===(nodes.map(_.member).sorted)
      }
    }
  }
}
