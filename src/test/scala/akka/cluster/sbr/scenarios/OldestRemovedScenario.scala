package akka.cluster.sbr.scenarios

import akka.cluster.Member
import akka.cluster.MemberStatus.Removed
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.SBRFailureDetector.Reachable
import akka.cluster.sbr.WorldView.Status
import akka.cluster.sbr.testImplicits._
import akka.cluster.sbr.{Node, WorldView}
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._

final case class OldestRemovedScenario(worldViews: List[WorldView], clusterSize: Int Refined Positive)

object OldestRemovedScenario {
  implicit val arbOldestRemovedScenario: Arbitrary[OldestRemovedScenario] = Arbitrary {
    def divergeWorldView(worldView: WorldView, allNodes: Set[Node], partition: Set[Node]): Arbitrary[WorldView] =
      Arbitrary {
        val otherNodes = allNodes -- partition

        val oldestNode = partition.toList.sortBy(_.member)(Member.ageOrdering).head

        chooseNum(1, 3)
          .map { n =>
            if (n === 1)
              // Remove oldest node
              worldView.memberRemoved(oldestNode.member.copy(Removed), Set.empty)
            else if (n === 2)
              // Oldest node is unreachable
              worldView.unreachableMember(oldestNode.member)
            else worldView // info not disseminated
          }
          .map { worldView =>
            // Change `self`
            val worldView0 = worldView.changeSelf(partition.head.member)

            otherNodes.foldLeft[WorldView](worldView0) {
              case (worldView, node) => worldView.unreachableMember(node.member)
            }
          }
      }

    for {
      initWorldView <- arbHealthyWorldView.arbitrary
      nodes = initWorldView.nodes
      partitions         <- splitCluster(nodes)
      divergedWorldViews <- partitions.traverse(divergeWorldView(initWorldView, nodes, _)).arbitrary
    } yield OldestRemovedScenario(divergedWorldViews, refineV[Positive](nodes.size).right.get)
  }
}
