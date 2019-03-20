package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._

class ReachableConsideredNodeSpec extends MySpec {
  "ReachableConsideredNode" - {
    "1 - should not affect the order" in {
      forAll { nodes: List[ReachableConsideredNode] =>
        nodes.sorted.map(_.node) shouldBe nodes.map(_.node).sorted
      }
    }
  }
}
