package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus.{Leaving, Up}
import org.scalacheck.Prop.propBoolean

class UnreachableQuorumSpec extends LithiumSpec {
  "UnreachableQuorum" must {
    "instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: Int, role: String) =>
        (quorumSize > 0) ==> {
          val nbrOfConsideredUnreachableNodes = worldView.unreachableNodesWithRole(role).count { node =>
            node.status == Up || node.status == Leaving
          }

          UnreachableQuorum(worldView, quorumSize, role) match {
            case UnreachableQuorum.None            => nbrOfConsideredUnreachableNodes == 0
            case UnreachableQuorum.PotentialQuorum => nbrOfConsideredUnreachableNodes >= quorumSize
            case UnreachableQuorum.SubQuorum       => nbrOfConsideredUnreachableNodes < quorumSize
          }
        }
      }
    }
  }
}
