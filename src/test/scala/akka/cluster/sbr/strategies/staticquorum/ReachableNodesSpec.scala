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
            val total =
              if (role.nonEmpty) reachableNodes.filter(_.node.roles.contains(role)).size
              else reachableNodes.length

            total should be >= quorumSize.value

            reachableNodes.toSortedSet shouldEqual reachability.reachableNodes

          case Right(ReachableSubQuorum(reachableNodes)) =>
            val total =
              if (role.nonEmpty) reachableNodes.filter(_.node.roles.contains(role)).size
              else reachableNodes.length

            total should be < quorumSize.value
            reachableNodes.toSortedSet shouldEqual reachability.reachableNodes
        }
      }
    }
  }
}
