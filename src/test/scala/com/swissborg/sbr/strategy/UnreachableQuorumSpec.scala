package com.swissborg.sbr
package strategy

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

class UnreachableQuorumSpec extends SBSpec {
  "UnreachableQuorum" must {
    "instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: Int Refined Positive, role: String) =>
        UnreachableQuorum(worldView, quorumSize, role) match {
          case UnreachableQuorum.None =>
            worldView.consideredUnreachableNodesWithRole(role) shouldBe empty

          case UnreachableQuorum.PotentialQuorum =>
            worldView.consideredUnreachableNodesWithRole(role).size should be >= quorumSize.value

          case UnreachableQuorum.SubQuorum =>
            worldView.consideredUnreachableNodesWithRole(role).size should be < quorumSize.value
        }
      }
    }
  }
}
