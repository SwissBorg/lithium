package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus.{Leaving, Up}
import cats.implicits._
import com.swissborg.lithium.implicits._

sealed abstract private[lithium] class UnreachableQuorum

private[lithium] object UnreachableQuorum {

  def apply(worldView: WorldView, quorumSize: Int, role: String): UnreachableQuorum = {
    val nbrOfConsideredUnreachableNodes = worldView.unreachableNodesWithRole(role).count { node =>
      node.status === Up || node.status === Leaving
    }

    if (nbrOfConsideredUnreachableNodes === 0) None
    else {
      if (nbrOfConsideredUnreachableNodes >= quorumSize)
        PotentialQuorum
      else
        SubQuorum
    }
  }

  case object PotentialQuorum extends UnreachableQuorum
  case object SubQuorum       extends UnreachableQuorum
  case object None            extends UnreachableQuorum
}
