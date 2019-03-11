package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.{MySpec, WorldView}
import akka.cluster.sbr.ArbitraryInstances._

class NodesMajoritySpec extends MySpec {
  "NodesMajority" - {
    "1 - should instantiate the correct instance" in {
      forAll { worldView: WorldView =>
        val totalNodes = worldView.allNodes.size
        val majority = if (totalNodes == 0) 0 else totalNodes / 2 + 1

        NodesMajority(worldView) match {
          case ReachableMajority(reachableNodes) =>
            if (reachableNodes.size >= majority) {
              reachableNodes shouldEqual worldView.reachableNodes
            } else
              fail(
                s"Not a reachable majority.\n${reachableNodes.size} nodes is not a majority within a cluster of ${worldView.allNodes.size} nodes."
              )

          case UnreachableMajority(unreachableNodes) =>
            if (unreachableNodes.size >= majority) {
              unreachableNodes shouldEqual worldView.unreachableNodes
            } else
              fail(
                s"Not an unreachable majority.\n${unreachableNodes.size} nodes is not a majority within a cluster of ${worldView.allNodes.size} nodes."
              )

          case NoMajority =>
            if (worldView.reachableNodes.size < majority && worldView.unreachableNodes.size < majority) succeed
            else fail
        }
      }
    }
  }
}
