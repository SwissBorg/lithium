package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.implicits._
import cats.implicits._

class UnreachableNodeSpec extends SBSpec {
  "UnreachableNode" must {
    "not affect the order" in {
      forAll { unreachableNodes: List[UnreachableNode] =>
        unreachableNodes.sorted.map(_.member) should ===(unreachableNodes.map(_.member).sorted)
      }
    }
  }
}
