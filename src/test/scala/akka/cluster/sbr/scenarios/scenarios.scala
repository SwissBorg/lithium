package akka.cluster.sbr

import akka.cluster.Member
import akka.cluster.sbr.utils.splitIn
import cats.data.{NonEmptyList, NonEmptySet}
import eu.timepit.refined._
import eu.timepit.refined.numeric.Positive
import org.scalacheck.Gen
import org.scalacheck.Gen._

package object scenarios {

  /**
   * Split the nodes into n sub-clusters, where 1 <= n <= #nodes.
   */
  def splitCluster(nodes: NonEmptySet[Member]): Gen[NonEmptyList[NonEmptySet[Member]]] =
    for {
      // Split the allNodes in `nSubCluster`.
      nSubClusters <- chooseNum(1, nodes.length).map(refineV[Positive](_).right.get) // always > 1
      subClusters  <- splitIn(nSubClusters, nodes).arbitrary
    } yield subClusters
}
