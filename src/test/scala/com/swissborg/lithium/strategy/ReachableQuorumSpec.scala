package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus.{Leaving, Up}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

class ReachableQuorumSpec extends LithiumSpec {
  "ReachableQuorum" must {
    "instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: Int Refined Positive, role: String) =>
        val nbrOfConsideredReachableNodes = worldView.reachableNodesWithRole(role).count { node =>
          node.status == Up || node.status == Leaving
        }

        ReachableQuorum(worldView, quorumSize, role) match {
          case ReachableQuorum.Quorum =>
            nbrOfConsideredReachableNodes should be >= quorumSize.value

          case ReachableQuorum.NoQuorum =>
            nbrOfConsideredReachableNodes should be < quorumSize.value
        }
      }
    }
  }
}
