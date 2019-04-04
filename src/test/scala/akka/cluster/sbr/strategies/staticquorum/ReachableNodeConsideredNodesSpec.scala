package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.strategies.staticquorum.ArbitraryInstances._
import akka.cluster.sbr.{MySpec, WorldView}

class ReachableNodeConsideredNodesSpec extends MySpec {
  "ReachableNodes" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: QuorumSize, role: String) =>
        ReachableNodes(worldView, quorumSize, role) match {
          case Left(NoReachableNodesError) =>
            worldView.consideredReachableNodesWithRole(role) shouldBe empty

          case Right(ReachableQuorum) =>
            worldView.consideredReachableNodesWithRole(role).size should be >= quorumSize.value

          case Right(ReachableSubQuorum) =>
            worldView.consideredReachableNodesWithRole(role).size should be < quorumSize.value
        }
      }
    }
  }
}
