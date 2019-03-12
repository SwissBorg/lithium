package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.strategies.staticquorum.ArbitraryInstances._
import akka.cluster.sbr.{MySpec, WorldView}

class ReachableNodesSpec extends MySpec {
  "ReachableNodes" - {
    "1 - should instantiate the correct instance" in {
      forAll { (reachability: WorldView, quorumSize: QuorumSize, role: String) =>
        ReachableNodes(reachability, quorumSize, role) match {
          case Left(NoReachableNodesError) =>
            reachability.reachableNodesWithRole(role) shouldBe empty

          case Right(ReachableQuorum(reachableNodes)) =>
            reachableNodes.length should be >= quorumSize.value
            reachableNodes.toSortedSet shouldEqual reachability.reachableNodesWithRole(role)

          case Right(ReachableSubQuorum(reachableNodes)) =>
            reachableNodes.length should be < quorumSize.value
            reachableNodes.toSortedSet shouldEqual reachability.reachableNodesWithRole(role)
        }
      }
    }
  }
}
