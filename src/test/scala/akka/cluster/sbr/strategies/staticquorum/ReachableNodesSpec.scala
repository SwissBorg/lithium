package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.strategies.staticquorum.ArbitraryInstances._
import akka.cluster.sbr.{MySpec, WorldView}

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
