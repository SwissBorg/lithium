package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.implicits._

class ReachableConsideredNodeSpec extends MySpec {
  "ReachableConsideredNode" - {
    "1 - should not affect the order" in {
      forAll { nodes: List[ReachableConsideredNode] =>
        nodes.sorted.map(_.node) should ===(nodes.map(_.node).sorted)
      }
    }
  }
}
