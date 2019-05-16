package com.swissborg.sbr.strategies.staticquorum

import com.swissborg.sbr.strategies.staticquorum.ArbitraryInstances._
import com.swissborg.sbr.{SBSpec, WorldView}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

class ReachableNodesSpec extends SBSpec {
  "ReachableNodes" must {
    "instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: Int Refined Positive, role: String) =>
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
