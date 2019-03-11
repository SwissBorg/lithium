package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._

class UnreachableNodesSpec extends MySpec {
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
