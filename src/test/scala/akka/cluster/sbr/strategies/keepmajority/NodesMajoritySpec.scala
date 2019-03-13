package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.{MySpec, WorldView}
import akka.cluster.sbr.strategies.keepmajority.ArbitraryInstances._
import akka.cluster.sbr.strategies.keepmajority.KeepMajority.Config

class NodesMajoritySpec extends MySpec {
  "NodesMajority" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, config: Config) =>
        val totalNodes = worldView.allNodesWithRole(config.role).size
        val majority = if (totalNodes == 0) 1 else totalNodes / 2 + 1

        NodesMajority(worldView, config.role) match {
          case ReachableMajority(reachableNodes) =>
            if (reachableNodes.size >= majority) {
              reachableNodes shouldEqual worldView.reachableNodesWithRole(config.role)
            } else
              fail(
                s"Not a reachable majority.\n${reachableNodes.size} nodes is not a majority within a cluster of ${worldView.allNodesWithRole(config.role).size} nodes."
              )

          case ReachableLowestAddress(reachableNodes) =>
            if (reachableNodes.size == majority - 1) {
              reachableNodes shouldEqual worldView.reachableNodesWithRole(config.role)
            } else {
              fail(
                s"Not a reachable near-majority.\n${reachableNodes.size} nodes is not a near-majority within a cluster of ${worldView.allNodesWithRole(config.role).size} nodes."
              )
            }

          case UnreachableMajority(unreachableNodes) =>
            if (unreachableNodes.size >= majority) {
              unreachableNodes shouldEqual worldView.unreachableNodesWithRole(config.role)
            } else
              fail(
                s"Not an unreachable majority.\n${unreachableNodes.size} nodes is not a majority within a cluster of ${worldView.allNodes.size} nodes."
              )

          case UnreachableLowestAddress(unreachableNodes) =>
            if (unreachableNodes.size == majority - 1) {
              unreachableNodes shouldEqual worldView.unreachableNodesWithRole(config.role)
            } else
              fail(
                s"Not an unreachable near-majority.\n${unreachableNodes.size} nodes is not a near-majority within a cluster of ${worldView.allNodes.size} nodes."
              )

          case NoMajority =>
            if (worldView.reachableNodesWithRole(config.role).size < majority &&
                worldView.unreachableNodesWithRole(config.role).size < majority) succeed
            else fail
        }
      }
    }
  }
}
