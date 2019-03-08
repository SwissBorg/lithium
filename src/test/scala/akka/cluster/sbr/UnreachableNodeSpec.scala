package akka.cluster.sbr

import org.scalacheck.Prop._
import org.scalacheck.Properties
import akka.cluster.sbr.ArbitraryInstances._

class UnreachableNodeSpec extends Properties("UnreachableNode") {
  property("ordering") = forAll { unreachableNodes: List[UnreachableNode] =>
    unreachableNodes.sorted.map(_.node) == unreachableNodes.map(_.node).sorted
  }
}
