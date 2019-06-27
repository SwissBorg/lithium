package com.swissborg.sbr

import cats.implicits._
import com.swissborg.sbr.testImplicits._

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
