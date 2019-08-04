package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus.{Leaving, Up}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

class UnreachableQuorumSpec extends LithiumSpec {
  "UnreachableQuorum" must {
    "instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: Int Refined Positive, role: String) =>
        val nbrOfConsideredUnreachableNodes = worldView.unreachableNodesWithRole(role).count { node =>
          node.status == Up || node.status == Leaving
        }

        UnreachableQuorum(worldView, quorumSize, role) match {
          case UnreachableQuorum.None =>
            nbrOfConsideredUnreachableNodes shouldBe 0

          case UnreachableQuorum.PotentialQuorum =>
            nbrOfConsideredUnreachableNodes should be >= quorumSize.value

          case UnreachableQuorum.SubQuorum =>
            nbrOfConsideredUnreachableNodes should be < quorumSize.value
        }
      }
    }
  }
}
