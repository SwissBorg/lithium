package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.strategies.keepmajority.ArbitraryInstances._
import akka.cluster.sbr.strategies.keepmajority.KeepMajorityView.{LowestAddressIsNotConsidered, NoMajority}
import akka.cluster.sbr.{MySpec, WorldView}

class KeepMajorityViewSpec extends MySpec {
  "NodesMajority" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, keepMajority: KeepMajority) =>
        val totalNodes = worldView.allConsideredNodesWithRole(keepMajority.role).size
        val majority   = if (totalNodes == 0) 1 else totalNodes / 2 + 1

        KeepMajorityView(worldView, keepMajority.role)
          .map {
            case ReachableMajority =>
              worldView.reachableConsideredNodesWithRole(keepMajority.role).size should be >= majority

            case ReachableLowestAddress =>
              worldView.reachableConsideredNodesWithRole(keepMajority.role).size should ===(majority - 1)

            case UnreachableMajority =>
              worldView.unreachableNodesWithRole(keepMajority.role).size should be >= majority

            case UnreachableLowestAddress =>
              worldView.unreachableNodesWithRole(keepMajority.role).size should ===(majority - 1)
          }
          .handleError {
            case NoMajority =>
              if (worldView.reachableConsideredNodesWithRole(keepMajority.role).size < majority &&
                  worldView.unreachableNodesWithRole(keepMajority.role).size < majority) succeed
              else fail

            case LowestAddressIsNotConsidered(node) => fail(s"$node") // "should" never happen
          }
      }
    }
  }
}
