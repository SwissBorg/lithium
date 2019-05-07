package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.strategies.staticquorum.ArbitraryInstances._
import akka.cluster.sbr.{SBSpec, WorldView}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

class ReachableNodesSpec extends SBSpec {
  "ReachableNodes" must {
    "instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: Int Refined Positive, role: String) =>
        ReachableNodes(worldView, quorumSize.value, role) match {
          case ReachableQuorum =>
            worldView.consideredReachableNodesWithRole(role).size should be >= quorumSize.value

          case ReachableSubQuorum =>
            worldView.consideredReachableNodesWithRole(role).size should be < quorumSize.value
        }
      }
    }
  }
}
