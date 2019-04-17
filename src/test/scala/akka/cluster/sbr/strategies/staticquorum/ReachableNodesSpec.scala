package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.strategies.staticquorum.ArbitraryInstances._
import akka.cluster.sbr.{MySpec, WorldView}

class ReachableNodesSpec extends MySpec {
  "ReachableNodes" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: QuorumSize, role: String) =>
        ReachableNodes(worldView, quorumSize, role) match {
          case ReachableQuorum =>
            worldView.consideredReachableNodesWithRole(role).size should be >= quorumSize.value

          case ReachableSubQuorum =>
            worldView.consideredReachableNodesWithRole(role).size should be < quorumSize.value
        }
      }
    }
  }
}
