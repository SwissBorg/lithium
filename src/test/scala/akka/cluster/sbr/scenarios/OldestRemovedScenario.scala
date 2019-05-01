package akka.cluster.sbr.scenarios

import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.Member
import akka.cluster.MemberStatus.{Exiting, Removed}
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.testImplicits._
import akka.cluster.sbr.{Node, ReachableNode, WorldView}
import cats.data.{NonEmptyList, NonEmptySet}
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._

final case class OldestRemovedScenario(worldViews: NonEmptyList[WorldView], clusterSize: Int Refined Positive)

object OldestRemovedScenario {
  implicit val arbOldestRemovedScenario: Arbitrary[OldestRemovedScenario] = Arbitrary {
    def divergeWorldView(worldView: WorldView,
                         allNodes: NonEmptySet[Node],
                         partition: NonEmptySet[Node]): Arbitrary[WorldView] = Arbitrary {
      val otherNodes = allNodes -- partition

      val oldestNode = partition.toList.sortBy(_.member)(Member.ageOrdering).head

      chooseNum(1, 3)
        .map { n =>
          if (n === 1)
            // Remove oldest node
            worldView.memberEvent(MemberRemoved(oldestNode.member.copy(Removed), Exiting), Set.empty)
          else if (n === 2)
            // Oldest node is unreachable
            worldView.unreachableMember(oldestNode.member)
          else worldView // info not disseminated
        }
        .map { worldView =>
          // Change `self`
          val worldView0 = worldView.copy(
            selfNode = ReachableNode(partition.head.member), // only clean partitions // todo correct seenBy
            otherNodes = worldView.otherNodes - partition.head + (worldView.selfNode -> worldView.selfSeenBy) // add old self and remove new one
          )

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
    } yield OldestRemovedScenario(divergedWorldViews, refineV[Positive](nodes.length).right.get)
  }
}
