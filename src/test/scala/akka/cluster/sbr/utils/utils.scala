package akka.cluster.sbr

import cats.Order
import cats.data.{NonEmptyList, NonEmptySet}
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._
import cats.implicits._

package object utils {

  /**
   * Splits `as` in `parts` parts of arbitrary sizes.
   * If `parts` is less than or more than the size of `as` it will return `NonEmptySet(as)`.
   */
  def splitIn[A](parts: Long Refined Positive, as: NonEmptySet[A]): Arbitrary[NonEmptyList[NonEmptySet[A]]] =
    Arbitrary {
      if (parts <= 1 || parts > as.length) const(NonEmptyList.of(as))
      else {
        for {
          takeN <- chooseNum(1, as.size - parts)
          newSet = as.toSortedSet.take(takeN.toInt)
          newSets <- splitIn(refineV[Positive](parts - takeN).right.get, // trust me
                             NonEmptySet.fromSetUnsafe(as.toSortedSet -- newSet)).arbitrary
        } yield NonEmptySet.fromSetUnsafe(newSet) :: newSets
      }
    }
}
