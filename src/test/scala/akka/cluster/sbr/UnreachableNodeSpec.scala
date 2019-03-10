package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FreeSpec, Matchers}

class UnreachableNodeSpec extends FreeSpec with Matchers with PropertyChecks {
  "UnreachableNode" - {
    "1 - should not affect the order" in {
      forAll { unreachableNodes: List[UnreachableNode] =>
        unreachableNodes.sorted.map(_.node) shouldBe unreachableNodes.map(_.node).sorted
      }
    }
  }
}
