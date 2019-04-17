package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.strategies.keepmajority.ArbitraryInstances._
import akka.cluster.sbr.strategies.keepmajority.KeepMajorityView.NoMajority
import akka.cluster.sbr.{MySpec, WorldView}

class KeepMajorityViewSpec extends MySpec {
  "NodesMajority" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, keepMajority: KeepMajority) =>
        val totalNodes = worldView.consideredNodesWithRole(keepMajority.role).size
        val majority   = if (totalNodes === 0) 1 else totalNodes / 2 + 1

        KeepMajorityView(worldView, keepMajority.role)
          .map {
            case ReachableMajority =>
              worldView.consideredReachableNodesWithRole(keepMajority.role).size should be >= majority

            case ReachableLowestAddress =>
              worldView.consideredReachableNodesWithRole(keepMajority.role).size should ===(majority - 1)

            case UnreachableMajority =>
              worldView.consideredUnreachableNodesWithRole(keepMajority.role).size should be >= majority

            case UnreachableLowestAddress =>
              worldView.consideredUnreachableNodesWithRole(keepMajority.role).size should ===(majority - 1)
          }
          .handleError {
            case NoMajority =>
              if (worldView.consideredReachableNodesWithRole(keepMajority.role).size < majority &&
                  worldView.consideredUnreachableNodesWithRole(keepMajority.role).size < majority) succeed
              else fail
          }
      }
    }
  }
}
