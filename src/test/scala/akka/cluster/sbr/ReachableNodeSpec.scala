package akka.cluster.sbr

import org.scalacheck.Prop._
import org.scalacheck.Properties
import akka.cluster.sbr.ArbitraryInstances._

class ReachableNodeSpec extends Properties("ReachableNode") {
  property("ordering") = forAll { reachableNodes: List[ReachableNode] =>
    reachableNodes.sorted.map(_.node) == reachableNodes.map(_.node).sorted
  }
}
