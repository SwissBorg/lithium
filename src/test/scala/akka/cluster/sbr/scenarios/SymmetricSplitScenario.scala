package akka.cluster.sbr.scenarios

import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.Member
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.WorldView
import cats.data.{NonEmptyList, NonEmptySet}
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

    /**
     * Yields a [[WorldView]] that based on `worldView`
     * that sees all the nodes not in the `partition`
     * as unreachable.
     */
    def divergeWorldView(worldView: WorldView,
                         allNodes: NonEmptySet[Member],
                         partition: NonEmptySet[Member]): WorldView = {
      val otherNodes = allNodes -- partition

      // Change `self`
      val worldView0 = worldView.copy(
        self = partition.head,
        otherStatuses = worldView.otherStatuses + (worldView.self -> worldView.selfStatus) - partition.head // add old self and remove new one
      )

      otherNodes.foldLeft[WorldView](worldView0) {
        case (worldView, node) => worldView.reachabilityEvent(UnreachableMember(node))
      }
    }

    for {
      healthyWorldView <- arbHealthyWorldView.arbitrary

      allNodes = healthyWorldView.allStatuses.keys

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes)

      // Each sub-allNodes sees the other nodes as unreachable.
      divergedWorldViews = partitions.map(divergeWorldView(healthyWorldView, allNodes, _))
    } yield SymmetricSplitScenario(divergedWorldViews, refineV[Positive](allNodes.length).right.get)
  }

}
