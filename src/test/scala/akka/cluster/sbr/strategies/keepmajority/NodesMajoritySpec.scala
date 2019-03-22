package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.{MySpec, WorldView}
import akka.cluster.sbr.strategies.keepmajority.ArbitraryInstances._
import akka.cluster.sbr.strategies.keepmajority.KeepMajority.Config
import akka.cluster.sbr.strategies.keepmajority.NodesMajority.{LowestAddressIsNotConsidered, NoMajority}
import cats.implicits._

class NodesMajoritySpec extends MySpec {
  "NodesMajority" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, config: Config) =>
        val totalNodes = worldView.allConsideredNodesWithRole(config.role).size
        val majority   = if (totalNodes == 0) 1 else totalNodes / 2 + 1

        NodesMajority(worldView, config.role)
          .map {
            case ReachableMajority =>
              worldView.reachableConsideredNodesWithRole(config.role).size should be >= majority

            case ReachableLowestAddress =>
              worldView.reachableConsideredNodesWithRole(config.role).size should ===(majority - 1)

            case UnreachableMajority =>
              worldView.unreachableNodesWithRole(config.role).size should be >= majority

            case UnreachableLowestAddress =>
              worldView.unreachableNodesWithRole(config.role).size should ===(majority - 1)
          }
          .handleError {
            case NoMajority =>
              if (worldView.reachableConsideredNodesWithRole(config.role).size < majority &&
                  worldView.unreachableNodesWithRole(config.role).size < majority) succeed
              else fail

            case LowestAddressIsNotConsidered(node) => fail // "should" never happen
          }
      }
    }
  }
}
