package com.swissborg.lithium

import cats.implicits._
import com.swissborg.lithium.testImplicits._

class ReachableNodeSpec extends LithiumSpec {
  "ReachableNode" must {
    "not affect the order" in {
      forAll { reachableNodes: List[ReachableNode] =>
        reachableNodes.sorted.map(_.member) should ===(reachableNodes.map(_.member).sorted)
      }
    }
  }
}
