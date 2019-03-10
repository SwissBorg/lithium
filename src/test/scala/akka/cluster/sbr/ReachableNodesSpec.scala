package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FreeSpec, Matchers}

class ReachableNodesSpec extends FreeSpec with Matchers with PropertyChecks {
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
