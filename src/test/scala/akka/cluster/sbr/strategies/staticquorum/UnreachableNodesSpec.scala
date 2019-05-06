package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.strategies.staticquorum.ArbitraryInstances._
import akka.cluster.sbr.{SBSpec, WorldView}

class UnreachableNodesSpec extends SBSpec {
  "UnreachableNodes" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: QuorumSize, role: String) =>
        UnreachableNodes(worldView, quorumSize, role) match {
          case EmptyUnreachable =>
            worldView.consideredUnreachableNodesWithRole(role) shouldBe empty

          case UnreachablePotentialQuorum =>
            worldView.consideredUnreachableNodesWithRole(role).size should be >= quorumSize.value

          case UnreachableSubQuorum =>
            worldView.consideredUnreachableNodesWithRole(role).size should be < quorumSize.value
        }
      }
    }
  }
}
