package com.swissborg.lithium

import akka.cluster.Member
import akka.cluster.MemberStatus.{Exiting, Leaving, Removed}
import akka.cluster.swissborg.EitherValues
import cats.data.NonEmptySet
import cats.implicits._
import com.swissborg.lithium.testImplicits._
import com.swissborg.lithium.utils._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen.someOf
import org.scalacheck.{Arbitrary, Gen}

sealed abstract class Scenario {
  def worldViews: List[WorldView]

  def clusterSize: Int Refined Positive
}

final case class OldestRemovedDisseminationScenario(worldViews: List[WorldView], clusterSize: Int Refined Positive)
    extends Scenario

object OldestRemovedDisseminationScenario extends EitherValues {
  implicit val arbOldestRemovedScenario: Arbitrary[OldestRemovedDisseminationScenario] = {
    def divergeWorldView(
      allMembers: NonEmptySet[Member]
    )(partition: NonEmptySet[Member]): Arbitrary[Option[WorldView]] =
      Arbitrary {
        val otherNodes = allMembers -- partition

        val oldestMember = allMembers.toList.sorted(Member.ageOrdering).head

        // Change `self`
        val baseWorldView =
          WorldView.fromNodes(ReachableNode(partition.head),
                              partition.tail.map(ReachableNode(_): Node) ++ otherNodes.map(UnreachableNode(_): Node))

        def oldestRemoved =
          if (baseWorldView.selfUniqueAddress === oldestMember.uniqueAddress) {
            None
          } else {
            Some(baseWorldView.removeMember(oldestMember.copy(Removed)))
          }

        def oldestNotRemoved =
          Some(baseWorldView.addOrUpdate(oldestMember.copy(Leaving).copy(Exiting)))

        Gen.oneOf(oldestRemoved, oldestNotRemoved)
      }

    for {
      upMembers          <- arbAllUpWorldView.map(_.members)
      partitions         <- split(upMembers)
      divergedWorldViews <- partitions.traverse(divergeWorldView(upMembers))
    } yield OldestRemovedDisseminationScenario(divergedWorldViews.toList.flatten,
                                               refineV[Positive](upMembers.length).rightValue)
  }
}

final case class CleanPartitionScenario(worldViews: List[WorldView], clusterSize: Int Refined Positive) extends Scenario

object CleanPartitionScenario extends EitherValues {

  /**
   * Generates clean partition scenarios where the allNodes is split
   * in multiple sub-clusters and where each one sees the rest as
   * unreachable.
   */
  implicit val arbSplitScenario: Arbitrary[CleanPartitionScenario] = {
    def partitionedWorldView(allMembers: NonEmptySet[Member])(partition: NonEmptySet[Member]): WorldView = {
      val otherMembers = allMembers -- partition

      WorldView.fromNodes(ReachableNode(partition.head),
                          partition.tail.map(ReachableNode(_): Node) ++ otherMembers.map(UnreachableNode(_): Node))
    }

    for {
      members <- arbNonEmptySet[Member]

      // Split the allNodes in `nSubCluster`.
      partitions <- split(members)

      // Each sub-allNodes sees the other nodes as unreachable.
      partitionedWorldViews = partitions.map(partitionedWorldView(members))
    } yield CleanPartitionScenario(partitionedWorldViews.toList, refineV[Positive](members.length).rightValue)
  }

}

final case class UpDisseminationScenario(worldViews: List[WorldView], clusterSize: Int Refined Positive)
    extends Scenario

object UpDisseminationScenario extends EitherValues {
  implicit val arbUpDisseminationScenario: Arbitrary[UpDisseminationScenario] = {

    /**
     * Yields a [[WorldView]] that based on `worldView`
     * that sees all the nodes not in the `partition`
     * as unreachable and sees some members up that others
     * do not see.
     */
    def divergeWorldView(allMembers: NonEmptySet[Member], allMembersUp: NonEmptySet[Member], oldestMemberUp: Member)(
      partition: NonEmptySet[Member]
    ): Arbitrary[WorldView] = Arbitrary {
      val otherMembers = allMembers -- partition

      val baseWorldView = WorldView
        .fromNodes(ReachableNode(partition.head),
                   partition.tail.map(ReachableNode(_): Node) ++ otherMembers.map(UnreachableNode(_): Node))
        .addOrUpdate(oldestMemberUp)

      pickNonEmptySubset(allMembersUp).arbitrary.map(_.foldLeft(baseWorldView) {
        case (worldView, member) => worldView.addOrUpdate(member)
      })
    }

    for {
      joiningAndWeaklyUpMembers <- arbJoiningOrWeaklyUpOnlyWorldView.map(_.members)

      membersToUp <- pickNonEmptySubset(joiningAndWeaklyUpMembers)

      allMembersUp = membersToUp.zipWithIndex.map {
        case (member, upNumber) => member.copyUp(upNumber)
      }

      partitions <- split(joiningAndWeaklyUpMembers)

      oldestMember = allMembersUp.head // upNumber = 0

      divergedWorldViews <- partitions.traverse(divergeWorldView(joiningAndWeaklyUpMembers, allMembersUp, oldestMember))
    } yield UpDisseminationScenario(divergedWorldViews.toList,
                                    refineV[Positive](joiningAndWeaklyUpMembers.length).rightValue)
  }
}

final case class RemovedDisseminationScenario(worldViews: List[WorldView], clusterSize: Int Refined Positive)
    extends Scenario

object RemovedDisseminationScenario extends EitherValues {
  implicit val arbRemovedDisseminationScenario: Arbitrary[RemovedDisseminationScenario] = {

    /**
     * Yields a [[WorldView]] that based on `worldView`
     * that sees all the nodes not in the `partition`
     * as unreachable and sees some members as "exiting" that others
     * do not see.
     */
    def divergeWorldView(allMembers: NonEmptySet[Member],
                         membersToRemove: NonEmptySet[Member])(partition: NonEmptySet[Member]): Arbitrary[WorldView] = {
      val otherMembers = allMembers -- partition

      val baseWorldView = WorldView.fromNodes(
        ReachableNode(partition.head),
        partition.tail.map(ReachableNode(_): Node) ++ otherMembers.map(UnreachableNode(_): Node)
      )

      pickNonEmptySubset(membersToRemove).map { ms =>
        val worldView0 = ms.foldLeft(baseWorldView) {
          case (worldView, member) => worldView.addOrUpdate(member.copy(Leaving).copy(Exiting))
        }

        (membersToRemove -- ms).foldLeft(worldView0) {
          case (worldView, member) => worldView.addOrUpdate(member.copy(Removed))
        }
      }
    }

    for {
      allUpMembers <- arbAllUpWorldView.map(_.members)

      // Split the allNodes in `nSubCluster`.
      partitions <- split(allUpMembers)

      membersToRemove <- pickNonEmptySubset(allUpMembers)

      divergedWorldViews <- partitions.traverse(divergeWorldView(allUpMembers, membersToRemove))
    } yield RemovedDisseminationScenario(divergedWorldViews.toList, refineV[Positive](allUpMembers.length).rightValue)
  }
}

final case class WithNonCleanPartitions[S <: Scenario](worldViews: List[WorldView], clusterSize: Int Refined Positive)
    extends Scenario

object WithNonCleanPartitions {
  implicit def arbWithNonCleanPartitions[S <: Scenario: Arbitrary]: Arbitrary[WithNonCleanPartitions[S]] = Arbitrary {
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
