package com.swissborg.sbr

import cats.data.{NonEmptyList, NonEmptySet}
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._

import scala.collection.immutable.SortedSet

package object utils {

  /**
    * Split the `as` into n non-empty lists, where `1 <= n <= as.size`.
    */
  def split[A](as: NonEmptySet[A]): Arbitrary[NonEmptyList[NonEmptySet[A]]] = Arbitrary {
    for {
      // Split the allNodes in `nSubCluster`.
      nSubClusters <- chooseNum(1, as.length).map(refineV[Positive](_).right.get) // always > 1
      subClusters <- splitIn(nSubClusters, as).arbitrary
    } yield subClusters
  }

  /**
    * Splits `as` in `parts` parts of arbitrary sizes.
    * If `parts` is less than or more than the size of `as` it will return `NonEmptySet(as)`.
    */
  def splitIn[A](
      parts: Int Refined Positive,
      as: NonEmptySet[A]
  ): Arbitrary[NonEmptyList[NonEmptySet[A]]] =
    Arbitrary {
      if (parts <= 1 || parts > as.length) const(NonEmptyList.of(as))
      else {
        for {
          takeN <- chooseNum(1, as.length - parts + 1) // leave enough `as` to have at least 1 element per part
          newSet = as.toSortedSet.take(takeN.toInt)
          newSets <- splitIn(
            refineV[Positive](parts - 1).right.get, // parts > takeN
            NonEmptySet.fromSetUnsafe(as.toSortedSet -- newSet)
          ).arbitrary
        } yield NonEmptySet.fromSetUnsafe(newSet) :: newSets
      }
    }

  def pickNonEmptySubset[A: Ordering](as: NonEmptySet[A]): Arbitrary[NonEmptySet[A]] = Arbitrary {
    atLeastOne(as.toSortedSet).map(seq => NonEmptySet.fromSetUnsafe(SortedSet(seq: _*)))
  }
}
