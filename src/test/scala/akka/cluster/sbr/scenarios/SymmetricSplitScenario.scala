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

      otherNodes.foldLeft[WorldView](worldView) {
        case (worldView, node) => worldView.reachabilityEvent(UnreachableMember(node)).toTry.get
      }
    }

    for {
      healthyWorldView <- arbHealthyWorldView.arbitrary

      allNodes = NonEmptySet
        .fromSetUnsafe(healthyWorldView.allNodes) // HealthyWorldView has at least one node and all are reachable

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes)

      // Each sub-allNodes sees the other nodes as unreachable.
      divergedWorldViews = partitions.map(divergeWorldView(healthyWorldView, allNodes, _))
    } yield SymmetricSplitScenario(divergedWorldViews, refineV[Positive](allNodes.length).right.get)
  }

}
