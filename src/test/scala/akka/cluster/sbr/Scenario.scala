package akka.cluster.sbr

import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.Member
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.utils.splitIn
import cats.Order
import cats.data.{NonEmptyList, NonEmptySet}
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import org.scalacheck.Gen._
import org.scalacheck.{Arbitrary, Gen}
import shapeless.tag
import shapeless.tag.@@

final case class Scenario(worldViews: NonEmptyList[WorldView], clusterSize: Int Refined Positive)

object Scenario {
  sealed trait SymmetricSplitTag
  type SymmetricSplitScenario = Scenario @@ SymmetricSplitTag

  implicit val memberOrder: Order[Member] = Order.fromOrdering

  /**
   * Generates symmetric split scenarios where the allNodes is split
   * in multiple sub-clusters and where each one sees the rest as
   * unreachable.
   */
  implicit val arbSplitScenario: Arbitrary[SymmetricSplitScenario] = Arbitrary(
    for {
      healthyWorldView <- arbHealthyWorldView.arbitrary

      allNodes = NonEmptySet
        .fromSetUnsafe(healthyWorldView.allNodes) // HealthyWorldView has at least one node and all are reachable

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes)

      // Each sub-allNodes sees the other nodes as unreachable.
      divergedWorldViews = partitions.map(divergeWorldView(healthyWorldView, allNodes, _))
    } yield
      tag[SymmetricSplitTag][Scenario](
        Scenario(divergedWorldViews, refineV[Positive](allNodes.length).right.get)
      )
  )

  /**
   * Split the nodes into n sub-clusters, where 1 <= n <= #nodes.
   */
  private def splitCluster(nodes: NonEmptySet[Member]): Gen[NonEmptyList[NonEmptySet[Member]]] =
    for {
      // Split the allNodes in `nSubCluster`.
      nSubClusters <- chooseNum(1, nodes.length).map(refineV[Positive](_).right.get) // always > 1
      subClusters <- splitIn(nSubClusters, nodes).arbitrary
    } yield subClusters

  /**
   * Yields a [[WorldView]] that based on `worldView`
   * that sees all the nodes not in the `partition`
   * as unreachable.
   */
  private def divergeWorldView(worldView: WorldView,
                               allNodes: NonEmptySet[Member],
                               partition: NonEmptySet[Member]): WorldView = {
    val otherNodes = allNodes -- partition

    otherNodes.foldLeft[WorldView](worldView) {
      case (worldView, node) => worldView.reachabilityEvent(UnreachableMember(node))
    }
  }
}
