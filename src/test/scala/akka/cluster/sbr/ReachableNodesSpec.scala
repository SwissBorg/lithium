package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._

class ReachableNodesSpec extends MySpec {
  "ReachableNodes" - {
    "1 - should instantiate the correct instance" in {
      forAll { (reachability: WorldView, quorumSize: QuorumSize) =>
        ReachableNodes(reachability, quorumSize) match {
          case Left(NoReachableNodesError) =>
            reachability.reachableNodes shouldBe empty

          case Right(ReachableQuorum(reachableNodes)) =>
            reachableNodes.length should be >= quorumSize.value
            reachableNodes.toSortedSet shouldEqual reachability.reachableNodes

          case Right(ReachableSubQuorum(reachableNodes)) =>
            reachableNodes.length should be < quorumSize.value
            reachableNodes.toSortedSet shouldEqual reachability.reachableNodes
        }
      }
    }
  }
}
