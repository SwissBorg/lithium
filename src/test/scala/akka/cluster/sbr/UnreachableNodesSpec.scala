package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FreeSpec, Matchers}

class UnreachableNodesSpec extends FreeSpec with Matchers with PropertyChecks {
  "UnreachableNodes" - {
    "1 - should instantiate the correct instance" in {
      forAll { (reachability: WorldView, quorumSize: QuorumSize) =>
        UnreachableNodes(reachability, quorumSize) match {
          case EmptyUnreachable() =>
            reachability.unreachableNodes shouldBe empty

          case UnreachablePotentialQuorum(unreachableNodes) =>
            unreachableNodes.length should be >= quorumSize.value
            unreachableNodes.toSortedSet shouldEqual reachability.unreachableNodes

          case UnreachableSubQuorum(unreachableNodes) =>
            unreachableNodes.length should be < quorumSize.value
            unreachableNodes.toSortedSet shouldEqual reachability.unreachableNodes
        }
      }
    }
  }
}
