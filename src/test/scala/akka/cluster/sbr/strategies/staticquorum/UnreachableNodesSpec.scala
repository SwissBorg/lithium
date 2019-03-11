package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.strategies.staticquorum.ArbitraryInstances._
import akka.cluster.sbr.{MySpec, WorldView}

class UnreachableNodesSpec extends MySpec {
  "UnreachableNodes" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: QuorumSize) =>
        UnreachableNodes(worldView, quorumSize) match {
          case EmptyUnreachable() =>
            worldView.unreachableNodes shouldBe empty

          case StaticQuorumUnreachablePotentialQuorum(unreachableNodes) =>
            unreachableNodes.length should be >= quorumSize.value
            unreachableNodes.toSortedSet shouldEqual worldView.unreachableNodes

          case StaticQuorumUnreachableSubQuorum(unreachableNodes) =>
            unreachableNodes.length should be < quorumSize.value
            unreachableNodes.toSortedSet shouldEqual worldView.unreachableNodes
        }
      }
    }
  }
}
