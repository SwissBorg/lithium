package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FreeSpec, Matchers}

class ReachableNodeSpec extends FreeSpec with Matchers with PropertyChecks {
  "ReachableNode" - {
    "1 - should not affect the order" in {
      forAll { reachableNodes: List[ReachableNode] =>
        reachableNodes.sorted.map(_.node) shouldBe reachableNodes.map(_.node).sorted
      }
    }
  }
}
