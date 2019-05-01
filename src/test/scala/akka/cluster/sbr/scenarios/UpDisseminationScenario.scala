package akka.cluster.sbr.scenarios

import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.MemberStatus.{Joining, WeaklyUp}
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.testImplicits._
import akka.cluster.sbr.{Node, ReachableNode, WorldView}
import cats.data.{NonEmptyList, NonEmptySet}
import cats.implicits._
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._

import scala.collection.immutable.SortedSet

final case class UpDisseminationScenario(worldViews: NonEmptyList[WorldView])

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
        .map(_.filter(e => e.member.status == Joining || e.member.status == WeaklyUp).foldLeft(worldView) {
          case (worldView, upEvent) =>
            worldView.memberEvent(MemberUp(upEvent.member.copyUp(Integer.MAX_VALUE)), Set.empty)
        })
        .map { worldView =>
          val otherNodes = allNodes -- partition

          // Change `self`
          val worldView0 = worldView.copy(
            selfNode = ReachableNode(partition.head.member), // only clean partitions // todo correct seenBy
            otherNodes = worldView.otherNodes - partition.head + (worldView.selfNode -> worldView.selfSeenBy) // add old self and remove new one
          )

          otherNodes.foldLeft[WorldView](worldView0) {
            case (worldView, node) => worldView.unreachableMember(node.member)
          }
        }

    for {
      initWorldView <- arbUpNumberConsistentWorldView.arbitrary

      allNodes = initWorldView.nodes // UpNumberConsistentWorldView has at least one node and all are reachable

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes)

      // Each sub-allNodes sees the other nodes as unreachable.

      divergedWorldViews <- partitions.traverse(divergeWorldView(initWorldView, allNodes, _)).arbitrary
    } yield UpDisseminationScenario(divergedWorldViews)
  }

  def pickStrictSubset[A: Ordering](as: NonEmptySet[A]): Arbitrary[SortedSet[A]] = Arbitrary {
    for {
      n      <- chooseNum(0, as.size - 1)
      subset <- pick(n.toInt, as.toSortedSet)
    } yield SortedSet(subset: _*)
  }
}
