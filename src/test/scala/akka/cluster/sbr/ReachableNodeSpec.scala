package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.implicits._
import cats.implicits._

class ReachableNodeSpec extends SBSpec {
  "ReachableNode" must {
    "not affect the order" in {
      forAll { reachableNodes: List[ReachableNode] =>
        reachableNodes.sorted.map(_.member) should ===(reachableNodes.map(_.member).sorted)
      }
    }
  }
}
