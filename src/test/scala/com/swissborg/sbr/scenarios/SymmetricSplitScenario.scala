package com.swissborg.sbr.scenarios

import akka.actor.Address
import cats.data.{NonEmptyList, NonEmptySet}
import com.swissborg.sbr.ArbitraryInstances._
import com.swissborg.sbr.{Node, ReachableNode, WorldView}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary

final case class SymmetricSplitScenario(worldViews: NonEmptyList[WorldView], clusterSize: Int Refined Positive)

object SymmetricSplitScenario {

  /**
   * Generates symmetric split scenarios where the allNodes is split
   * in multiple sub-clusters and where each one sees the rest as
   * unreachable.
   */
  implicit val arbSplitScenario: Arbitrary[SymmetricSplitScenario] = Arbitrary {

    def partitionedWorldView[N <: Node](nodes: NonEmptySet[N])(partition: NonEmptySet[N]): WorldView = {
      val otherNodes = nodes -- partition

      val worldView0 = WorldView.fromNodes(ReachableNode(partition.head.member),
                                           Set.empty,
                                           partition.tail.map(_ -> Set.empty[Address]).toMap)

      otherNodes.foldLeft[WorldView](worldView0) {
        case (worldView, node) => worldView.withUnreachableMember(node.member)
      }
    }

    for {
//      selfNode <- arbReachableNode.arbitrary
      allNodes <- arbNonEmptySet[ReachableNode].arbitrary
//      allNodes = nodes.add(selfNode)

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes)

      // Each sub-allNodes sees the other nodes as unreachable.
      partitionedWorldViews = partitions.map(partitionedWorldView(allNodes))
    } yield SymmetricSplitScenario(partitionedWorldViews, refineV[Positive](allNodes.length).right.get)
  }

}
