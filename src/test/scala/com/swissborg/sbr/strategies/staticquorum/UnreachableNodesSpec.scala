package com.swissborg.sbr.strategies.staticquorum

import com.swissborg.sbr.strategies.staticquorum.ArbitraryInstances._
import com.swissborg.sbr.{SBSpec, WorldView}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

class UnreachableNodesSpec extends SBSpec {
  "UnreachableNodes" must {
    "instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: Int Refined Positive, role: String) =>
        UnreachableNodes(worldView, quorumSize, role) match {
          case EmptyUnreachable =>
            worldView.consideredUnreachableNodesWithRole(role) shouldBe empty

          case UnreachablePotentialQuorum =>
            worldView.consideredUnreachableNodesWithRole(role).size should be >= quorumSize.value

          case UnreachableSubQuorum =>
            worldView.consideredUnreachableNodesWithRole(role).size should be < quorumSize.value
        }
      }
    }
  }
}
