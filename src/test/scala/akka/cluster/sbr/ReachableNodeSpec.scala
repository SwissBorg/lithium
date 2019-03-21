package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.implicits._

class ReachableNodeSpec extends MySpec {
  "ReachableNode" - {
    "1 - should not affect the order" in {
      forAll { reachableNodes: List[ReachableNode] =>
        reachableNodes.sorted.map(_.node) should ===(reachableNodes.map(_.node).sorted)
      }
    }
  }
}
