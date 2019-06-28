package com.swissborg.sbr

import cats.implicits._
import com.swissborg.sbr.testImplicits._

class UnreachableNodeSpec extends SBSpec {
  "UnreachableNode" must {
    "not affect the order" in {
      forAll { unreachableNodes: List[UnreachableNode] =>
        unreachableNodes.sorted.map(_.member) should ===(unreachableNodes.map(_.member).sorted)
      }
    }
  }
}
