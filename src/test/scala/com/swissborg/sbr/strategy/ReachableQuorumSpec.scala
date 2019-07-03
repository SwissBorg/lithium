package com.swissborg.sbr
package strategy

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

class ReachableQuorumSpec extends SBSpec {
  "ReachableQuorum" must {
    "instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: Int Refined Positive, role: String) =>
        ReachableQuorum(worldView, quorumSize, role) match {
          case ReachableQuorum.Quorum =>
            worldView.consideredReachableNodesWithRole(role).size should be >= quorumSize.value

          case ReachableQuorum.NoQuorum =>
            worldView.consideredReachableNodesWithRole(role).size should be < quorumSize.value
        }
      }
    }
  }
}
