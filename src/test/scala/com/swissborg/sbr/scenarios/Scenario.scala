package com.swissborg.sbr
package scenarios

import akka.cluster.Member
import akka.cluster.MemberStatus.{Joining, Removed, WeaklyUp}
import cats.data.{NonEmptyList, NonEmptySet}
import cats.implicits._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.utils._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen.someOf

sealed abstract class Scenario {
  def worldViews: NonEmptyList[WorldView]
  def clusterSize: Int Refined Positive
}

final case class OldestRemovedDisseminationScenario(
    worldViews: NonEmptyList[WorldView],
    clusterSize: Int Refined Positive
) extends Scenario

object OldestRemovedDisseminationScenario {
  implicit val arbOldestRemovedScenario: Arbitrary[OldestRemovedDisseminationScenario] = Arbitrary {
    def divergeWorldView(
        worldView: WorldView,
        allNodes: NonEmptySet[Node],
        partition: NonEmptySet[Node]
    ): Arbitrary[WorldView] =
      Arbitrary {
        val otherNodes = allNodes -- partition

        val oldestNode = partition.toList.sortBy(_.member)(Member.ageOrdering).head

        // Change `self`
        val worldViewWithChangedSelf = worldView.changeSelf(partition.head.member)

        val worldView0 = otherNodes.foldLeft(worldViewWithChangedSelf) {
          case (worldView, node) =>
            worldView.addOrUpdate(node.member).withUnreachableNode(node.member.uniqueAddress)
        }

        arbitrary[Boolean]
          .map { b =>
            if (b) {
              // Node sees the nodes removed.
              worldView0.removeMember(oldestNode.member.copy(Removed))
            } else {
              // Node does not see the node removed.
              worldView0.addOrUpdate(oldestNode.member)
            }
          }
      }

    for {
      initWorldView <- arbAllUpWorldView.arbitrary
      nodes = initWorldView.nodes
      partitions <- splitCluster(nodes)
      divergedWorldViews <- partitions.traverse(divergeWorldView(initWorldView, nodes, _)).arbitrary
    } yield
      OldestRemovedDisseminationScenario(
        divergedWorldViews,
        refineV[Positive](nodes.length).right.get
      )
  }
}

final case class CleanPartitionsScenario(
    worldViews: NonEmptyList[WorldView],
    clusterSize: Int Refined Positive
) extends Scenario

object CleanPartitionsScenario {

  /**
    * Generates clean partition scenarios where the allNodes is split
    * in multiple sub-clusters and where each one sees the rest as
    * unreachable.
    */
  implicit val arbSplitScenario: Arbitrary[CleanPartitionsScenario] = Arbitrary {

    def partitionedWorldView[N <: Node](
        nodes: NonEmptySet[N]
    )(partition: NonEmptySet[N]): WorldView = {
      val otherNodes = nodes -- partition

      val worldView0 =
        WorldView.fromNodes(ReachableNode(partition.head.member), partition.tail.map(identity))

      otherNodes.foldLeft[WorldView](worldView0) {
        case (worldView, node) =>
          worldView.addOrUpdate(node.member).withUnreachableNode(node.member.uniqueAddress)
      }
    }

    for {
      allNodes <- arbNonEmptySet[ReachableNode].arbitrary

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes)

      // Each sub-allNodes sees the other nodes as unreachable.
      partitionedWorldViews = partitions.map(partitionedWorldView(allNodes))
    } yield
      CleanPartitionsScenario(partitionedWorldViews, refineV[Positive](allNodes.length).right.get)
  }

}

final case class UpDisseminationScenario(
    worldViews: NonEmptyList[WorldView],
    clusterSize: Int Refined Positive
) extends Scenario

object UpDisseminationScenario {
  implicit val arbUpDisseminationScenario: Arbitrary[UpDisseminationScenario] = Arbitrary {

    /**
      * Yields a [[WorldView]] that based on `worldView`
      * that sees all the nodes not in the `partition`
      * as unreachable and sees some members up that others
      * do not see.
      */
    def divergeWorldView(worldView: WorldView, allNodes: NonEmptySet[Node], upNumber: Int)(
        partition: NonEmptySet[Node]
    ): Arbitrary[WorldView] = Arbitrary {
      val otherNodes = allNodes -- partition

      // Change `self`
      val worldViewWithChangedSelf = worldView.changeSelf(partition.head.member)

      val worldView0 = otherNodes.foldLeft[WorldView](worldViewWithChangedSelf) {
        case (worldView, node) =>
          worldView.addOrUpdate(node.member).withUnreachableNode(node.member.uniqueAddress)
      }

      pickNonEmptySubset(partition)
        .map(
          _.filter(e => e.member.status === Joining || e.member.status === WeaklyUp)
            .foldLeft((worldView0, upNumber)) {
              case ((worldView, upNumber), upEvent) =>
                (worldView.addOrUpdate(upEvent.member.copyUp(upNumber)), upNumber + 1)
            }
            ._1
        )
        .arbitrary
    }

    for {
      initWorldView <- arbJoiningOrWeaklyUpOnlyWorldView.arbitrary

      allNodes = initWorldView.nodes // all are reachable

      nodesToUp <- pickNonEmptySubset(allNodes).arbitrary
      oldestNode = nodesToUp.head

      // Move some random nodes to up.
      // Fix who the oldest node is else we get a cluster with an
      // inconsistent state where the oldest one might not be up.

      (allNodesWithNodesUp, upNumber) = (nodesToUp - oldestNode).foldLeft(
        (allNodes.diff(nodesToUp), 1) // 0 is for the oldest node
      ) {
        case ((allNodesWithNodesUp, upNumber), nodeToUp) =>
          (allNodesWithNodesUp + nodeToUp.updateMember(_.copyUp(upNumber)), upNumber + 1)
      }

      allNodes0 = NonEmptySet.fromSetUnsafe(
        allNodesWithNodesUp + oldestNode.updateMember(_.copyUp(0))
      )

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes0)

      divergedWorldViews <- partitions.zipWithIndex.traverse {
        case (partition, partitionNumber) =>
          // So that we do not have `upNumber` collisions.
          // A partition has at most `allNodes.length` size so it
          // will never up more than that.
          val upNumber0 = upNumber + (partitionNumber * allNodes.length)

          divergeWorldView(initWorldView, allNodes0, upNumber0)(partition)
      }.arbitrary
    } yield
      UpDisseminationScenario(divergedWorldViews, refineV[Positive](allNodes0.length).right.get)
  }
}

final case class WithNonCleanPartitions[S <: Scenario](
    worldViews: NonEmptyList[WorldView],
    clusterSize: Int Refined Positive
) extends Scenario

object WithNonCleanPartitions {
  implicit def arbWithNonCleanPartitions[S <: Scenario: Arbitrary]
      : Arbitrary[WithNonCleanPartitions[S]] = Arbitrary {
    for {
      scenario <- arbitrary[S]

      // Add some arbitrary indirectly-connected nodes to each partition.
      worldViews <- scenario.worldViews.traverse { worldView =>
        someOf(worldView.reachableNodes).map(_.foldLeft(worldView) {
          case (worldView, indirectlyConnectedNode) =>
            worldView.withIndirectlyConnectedNode(indirectlyConnectedNode.member.uniqueAddress)
        })
      }
    } yield WithNonCleanPartitions(worldViews, scenario.clusterSize)
  }
}
