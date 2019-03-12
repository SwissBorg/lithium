package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.strategies.staticquorum.ArbitraryInstances._
import akka.cluster.sbr.{MySpec, WorldView}

class UnreachableNodesSpec extends MySpec {
  "UnreachableNodes" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: QuorumSize, role: String) =>
        UnreachableNodes(worldView, quorumSize, role) match {
          case EmptyUnreachable() =>
            worldView.unreachableNodesWithRole(role) shouldBe empty

          case StaticQuorumUnreachablePotentialQuorum(unreachableNodes) =>
            unreachableNodes.length should be >= quorumSize.value
            unreachableNodes.toSortedSet shouldEqual worldView.unreachableNodesWithRole(role)

          case StaticQuorumUnreachableSubQuorum(unreachableNodes) =>
            unreachableNodes.length should be < quorumSize.value
            unreachableNodes.toSortedSet shouldEqual worldView.unreachableNodesWithRole(role)
        }
      }
    }
  }
}
