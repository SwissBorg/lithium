package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus.{Leaving, Up}
import org.scalacheck.Prop.propBoolean

class ReachableQuorumSpec extends LithiumSpec {
  "ReachableQuorum" must {
    "instantiate the correct instance" in {
      forAll { (worldView: WorldView, quorumSize: Int, role: String) =>
        (quorumSize > 0) ==> {
          val nbrOfConsideredReachableNodes = worldView.reachableNodesWithRole(role).count { node =>
            node.status == Up || node.status == Leaving
          }

          ReachableQuorum(worldView, quorumSize, role) match {
            case ReachableQuorum.Quorum   => nbrOfConsideredReachableNodes >= quorumSize
            case ReachableQuorum.NoQuorum => nbrOfConsideredReachableNodes < quorumSize
          }
        }
      }
    }
  }
}
