package akka.cluster.sbr.scenarios

import akka.cluster.MemberStatus.{Joining, WeaklyUp}
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.SBRFailureDetector.Reachable
import akka.cluster.sbr.WorldView.Status
import akka.cluster.sbr.testImplicits._
import akka.cluster.sbr.{Node, WorldView}
import cats.implicits._
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._

final case class UpDisseminationScenario(worldViews: List[WorldView])

object UpDisseminationScenario {
  implicit val arbUpDisseminationScenario: Arbitrary[UpDisseminationScenario] = Arbitrary {

    /**
     * Yields a [[WorldView]] that based on `worldView`
     * that sees all the nodes not in the `partition`
     * as unreachable and sees some members up that others
     * do not see.
     */
    def divergeWorldView(worldView: WorldView, allNodes: Set[Node], partition: Set[Node]): Arbitrary[WorldView] =
      pickStrictSubset(partition)
        .map(_.filter(e => e.member.status == Joining || e.member.status == WeaklyUp).foldLeft(worldView) {
          case (worldView, upEvent) =>
            worldView.updateMember(upEvent.member.copyUp(Integer.MAX_VALUE), Set.empty)
        })
        .map { worldView =>
          val otherNodes = allNodes -- partition

          // Change `self`
          val worldView0 = worldView.copy(
            selfUniqueAddress = partition.head.member.uniqueAddress, // only clean partitions // todo correct seenBy
            selfStatus = Status(partition.head.member, Reachable, Set.empty),
            otherMembersStatus = worldView.otherMembersStatus - partition.head.member.uniqueAddress + (worldView.selfNode.member.uniqueAddress -> worldView.selfStatus) // add old self and remove new one
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

  def pickStrictSubset[A](as: Set[A]): Arbitrary[Set[A]] = Arbitrary {
    for {
      n      <- chooseNum(0, as.size - 1)
      subset <- pick(n.toInt, as)
    } yield Set(subset: _*)
  }
}
