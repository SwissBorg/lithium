package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus._
import cats.syntax.all._
import com.swissborg.lithium.implicits._

sealed abstract private[lithium] class ReachableQuorum extends Product with Serializable

private[lithium] object ReachableQuorum {

  def apply(worldView: WorldView, quorumSize: Int, role: String): ReachableQuorum = {
    val nbrOfConsideredReachableNodes = worldView.reachableNodesWithRole(role).count { node =>
      node.status === Up || node.status === Leaving
    }

    if (nbrOfConsideredReachableNodes >= quorumSize) {
      Quorum
    } else {
      NoQuorum
    }
  }

  case object Quorum   extends ReachableQuorum
  case object NoQuorum extends ReachableQuorum
}
