package com.swissborg.sbr.scenarios

import akka.cluster.Member
import akka.cluster.MemberStatus.{Joining, Removed, WeaklyUp}
import cats.data.{NonEmptyList, NonEmptySet}
import cats.implicits._
import com.swissborg.sbr.ArbitraryInstances._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.testImplicits._
import com.swissborg.sbr.{Node, ReachableNode, WorldView}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen.{chooseNum, pick}

import scala.collection.immutable.SortedSet

sealed abstract class Scenario {
  def worldViews: NonEmptyList[WorldView]
  def clusterSize: Int Refined Positive
}

final case class OldestRemovedScenario(
    worldViews: NonEmptyList[WorldView],
    clusterSize: Int Refined Positive
) extends Scenario

object OldestRemovedScenario {
  implicit val arbOldestRemovedScenario: Arbitrary[OldestRemovedScenario] = Arbitrary {
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
    } yield OldestRemovedScenario(divergedWorldViews, refineV[Positive](nodes.length).right.get)
  }
}

final case class SymmetricSplitScenario(
    worldViews: NonEmptyList[WorldView],
    clusterSize: Int Refined Positive
) extends Scenario

object SymmetricSplitScenario {

  /**
    * Generates symmetric split scenarios where the allNodes is split
    * in multiple sub-clusters and where each one sees the rest as
    * unreachable.
    */
  implicit val arbSplitScenario: Arbitrary[SymmetricSplitScenario] = Arbitrary {

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
      SymmetricSplitScenario(partitionedWorldViews, refineV[Positive](allNodes.length).right.get)
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
    def divergeWorldView(
        worldView: WorldView,
        allNodes: NonEmptySet[Node],
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
            .foldLeft(worldView0) {
              case (worldView, upEvent) =>
                worldView.addOrUpdate(upEvent.member.copyUp(Integer.MAX_VALUE))
            }
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
      allNodes0 = NonEmptySet.fromSetUnsafe(
        allNodes.diff(nodesToUp) ++ (nodesToUp - oldestNode).map(
          _.updateMember(_.copyUp(Integer.MAX_VALUE))
        ) + oldestNode.updateMember(_.copyUp(0))
      )

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes0)

      divergedWorldViews <- partitions
        .traverse(divergeWorldView(initWorldView, allNodes0, _))
        .arbitrary
    } yield
      UpDisseminationScenario(divergedWorldViews, refineV[Positive](allNodes0.length).right.get)
  }

  def pickNonEmptySubset[A: Ordering](as: NonEmptySet[A]): Arbitrary[NonEmptySet[A]] = Arbitrary {
    for {
      n <- chooseNum(1, as.size)
      subset <- pick(n.toInt, as.toSortedSet)
    } yield NonEmptySet.fromSetUnsafe(SortedSet(subset: _*))
  }
}
