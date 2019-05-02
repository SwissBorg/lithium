package akka.cluster.sbr.scenarios

import akka.actor.Address
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.{Node, ReachableNode, WorldView}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._

final case class SymmetricSplitScenario(worldViews: List[WorldView], clusterSize: Int Refined Positive)

object SymmetricSplitScenario {

  /**
   * Generates symmetric split scenarios where the allNodes is split
   * in multiple sub-clusters and where each one sees the rest as
   * unreachable.
   */
  implicit val arbSplitScenario: Arbitrary[SymmetricSplitScenario] = Arbitrary {

    def partitionedWorldView[N <: Node](nodes: Set[N])(partition: Set[N]): WorldView = {
      val otherNodes = nodes -- partition

      val worldView0 = WorldView.fromNodes(ReachableNode(partition.head.member),
                                           Set.empty,
                                           partition.tail.map(_ -> Set.empty[Address]).toMap,
                                           Map.empty)

      otherNodes.foldLeft[WorldView](worldView0) {
        case (worldView, node) => worldView.unreachableMember(node.member)
      }
    }

    for {
      selfNode <- arbReachableNode.arbitrary
      nodes    <- arbitrary[Set[ReachableNode]]
      allNodes = nodes + selfNode

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes)

      // Each sub-allNodes sees the other nodes as unreachable.
      partitionedWorldViews = partitions.map(partitionedWorldView(allNodes))
    } yield SymmetricSplitScenario(partitionedWorldViews, refineV[Positive](allNodes.size).right.get)
  }

}
