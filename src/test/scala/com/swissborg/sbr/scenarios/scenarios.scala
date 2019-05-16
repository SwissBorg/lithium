package com.swissborg.sbr

import cats.data.{NonEmptyList, NonEmptySet}
import com.swissborg.sbr.utils.splitIn
import eu.timepit.refined._
import eu.timepit.refined.numeric.Positive
import org.scalacheck.Gen
import org.scalacheck.Gen._

package object scenarios {

  /**
   * Split the nodes into n sub-clusters, where 1 <= n <= #nodes.
   */
  def splitCluster[A](nodes: NonEmptySet[A]): Gen[NonEmptyList[NonEmptySet[A]]] =
    for {
      // Split the allNodes in `nSubCluster`.
      nSubClusters <- chooseNum(1, nodes.length).map(refineV[Positive](_).right.get) // always > 1
      subClusters  <- splitIn(nSubClusters, nodes).arbitrary
    } yield subClusters
}
