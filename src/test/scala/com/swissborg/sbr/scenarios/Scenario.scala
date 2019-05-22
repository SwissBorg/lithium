package com.swissborg.sbr.scenarios

import akka.actor.Address
import akka.cluster.Member
import akka.cluster.MemberStatus.{Leaving, Removed}
import cats.data.{NonEmptyList, NonEmptySet}
import cats.implicits._
import com.swissborg.sbr.ArbitraryInstances.{arbNonEmptySet, arbNonRemovedWorldView, arbUpNumberConsistentWorldView}
import com.swissborg.sbr.{Node, ReachableNode, WorldView}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Gen.{chooseNum, pick}

import akka.cluster.MemberStatus.{Joining, WeaklyUp}
import cats.data.{NonEmptyList, NonEmptySet}
import cats.implicits._
import com.swissborg.sbr.ArbitraryInstances._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.testImplicits._
import com.swissborg.sbr.{Node, WorldView}
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._

import scala.collection.immutable.SortedSet

sealed abstract class Scenario {
  def worldViews: NonEmptyList[WorldView]
  def clusterSize: Int Refined Positive
}

final case class OldestRemovedScenario(worldViews: NonEmptyList[WorldView], clusterSize: Int Refined Positive)
    extends Scenario

object OldestRemovedScenario {
  implicit val arbOldestRemovedScenario: Arbitrary[OldestRemovedScenario] = Arbitrary {
    def divergeWorldView(worldView: WorldView,
                         allNodes: NonEmptySet[Node],
                         partition: NonEmptySet[Node]): Arbitrary[WorldView] =
      Arbitrary {
        val otherNodes = allNodes -- partition

        val oldestNode = partition.toList.sortBy(_.member)(Member.ageOrdering).head.updateMember(_.copy(Leaving))

        chooseNum(1, 3)
          .map { n =>
            if (n === 1)
              // Remove oldest node
              worldView.removeMember(oldestNode.member.copy(Removed), Set.empty)
            else if (n === 2)
              // Oldest node is unreachable
              worldView.withUnreachableMember(oldestNode.member)
            else worldView // info not disseminated
          }
          .map { worldView =>
            // Change `self`
            val worldView0 = worldView.changeSelf(partition.head.member)

            otherNodes.foldLeft[WorldView](worldView0) {
              case (worldView, node) => worldView.withUnreachableMember(node.member)
            }
          }
      }

    for {
      initWorldView <- arbNonRemovedWorldView.arbitrary
      nodes = initWorldView.nodes
      partitions         <- splitCluster(nodes)
      divergedWorldViews <- partitions.traverse(divergeWorldView(initWorldView, nodes, _)).arbitrary
    } yield OldestRemovedScenario(divergedWorldViews, refineV[Positive](nodes.length).right.get)
  }
}

final case class SymmetricSplitScenario(worldViews: NonEmptyList[WorldView], clusterSize: Int Refined Positive)
    extends Scenario

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

final case class UpDisseminationScenario(worldViews: NonEmptyList[WorldView], clusterSize: Int Refined Positive)
    extends Scenario

object UpDisseminationScenario {
  implicit val arbUpDisseminationScenario: Arbitrary[UpDisseminationScenario] = Arbitrary {

    /**
     * Yields a [[WorldView]] that based on `worldView`
     * that sees all the nodes not in the `partition`
     * as unreachable and sees some members up that others
     * do not see.
     */
    def divergeWorldView(worldView: WorldView,
                         allNodes: NonEmptySet[Node],
                         partition: NonEmptySet[Node]): Arbitrary[WorldView] =
      pickStrictSubset(partition)
        .map(_.filter(e => e.member.status === Joining || e.member.status === WeaklyUp).foldLeft(worldView) {
          case (worldView, upEvent) =>
            worldView.updateMember(upEvent.member.copyUp(Integer.MAX_VALUE), Set.empty)
        })
        .map { worldView =>
          val otherNodes = allNodes -- partition

          // Change `self`
          val worldView0 = worldView.changeSelf(partition.head.member)

          otherNodes.foldLeft[WorldView](worldView0) {
            case (worldView, node) => worldView.withUnreachableMember(node.member)
          }
        }

    for {
      initWorldView <- arbUpNumberConsistentWorldView.arbitrary

      allNodes = initWorldView.nodes // UpNumberConsistentWorldView has at least one node and all are reachable

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes)

      // Each sub-allNodes sees the other nodes as unreachable.

      divergedWorldViews <- partitions.traverse(divergeWorldView(initWorldView, allNodes, _)).arbitrary
    } yield UpDisseminationScenario(divergedWorldViews, refineV[Positive](allNodes.length).right.get)
  }

  def pickStrictSubset[A: Ordering](as: NonEmptySet[A]): Arbitrary[SortedSet[A]] = Arbitrary {
    for {
      n      <- chooseNum(0, as.size - 1)
      subset <- pick(n.toInt, as.toSortedSet)
    } yield SortedSet(subset: _*)
  }
}
