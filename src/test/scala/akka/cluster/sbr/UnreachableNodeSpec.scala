package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.implicits._

class UnreachableNodeSpec extends MySpec {
  "UnreachableNode" - {
    "1 - should not affect the order" in {
      forAll { unreachableNodes: List[UnreachableNode] =>
        unreachableNodes.sorted.map(_.member) should ===(unreachableNodes.map(_.member).sorted)
      }
    }
  }
}
