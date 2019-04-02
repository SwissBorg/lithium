package akka.cluster.sbr.scenarios

import akka.cluster.ClusterEvent.{MemberRemoved, UnreachableMember}
import akka.cluster.Member
import akka.cluster.MemberStatus.{Exiting, Removed}
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.WorldView
import cats.data.{NonEmptyList, NonEmptySet}
import cats.implicits._
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import akka.cluster.sbr.testImplicits._

final case class OldestRemovedScenario(worldViews: NonEmptyList[WorldView], clusterSize: Int Refined Positive)

object OldestRemovedScenario {
  implicit val arbOldestRemovedScenario: Arbitrary[OldestRemovedScenario] = Arbitrary {
    def divergeWorldView(worldView: WorldView,
                         allNodes: NonEmptySet[Member],
                         partition: NonEmptySet[Member]): Arbitrary[WorldView] = Arbitrary {
      val otherNodes = allNodes -- partition

      val oldestNode = partition.toList.sorted(Member.ageOrdering).head

      chooseNum(1, 3)
        .map { n =>
          if (n === 1)
            // Remove oldest node
            worldView
              .memberEvent(MemberRemoved(oldestNode.copy(Removed), Exiting))
              .getOrElse(worldView) // info not disseminated before partition
          else if (n === 2)
            // Oldest node is unreachable
            worldView
              .reachabilityEvent(UnreachableMember(oldestNode))
              .getOrElse(worldView) // unreachable just after partition
          else worldView
        }
        .map { worldView =>
          // Change `self`
          val worldView0 = worldView.copy(
            self = partition.head,
            otherStatuses = worldView.otherStatuses + (worldView.self -> worldView.selfStatus) - partition.head // add old self and remove new one
          )

          otherNodes.foldLeft[WorldView](worldView0) {
            case (worldView, node) => worldView.reachabilityEvent(UnreachableMember(node)).toTry.get
          }
        }
    }

    for {
      initWorldView <- arbHealthyWorldView.arbitrary
      allNodes = NonEmptySet.fromSetUnsafe(initWorldView.allConsideredNodes)
      partitions         <- splitCluster(allNodes)
      divergedWorldViews <- partitions.traverse(divergeWorldView(initWorldView, allNodes, _)).arbitrary
    } yield OldestRemovedScenario(divergedWorldViews, refineV[Positive](allNodes.length).right.get)
  }
}
