package com.swissborg.lithium

package utils

import cats.data._
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.auto._
import org.scalacheck.Arbitrary

import scala.collection.immutable.SortedSet

class UtilSpec extends SBSpec {
  "Util" must {
    "splitIn" in {
      forAll { (parts: Int Refined Positive, head: Int, tail: SortedSet[Int]) =>
        val nes = NonEmptySet(head, tail)

        // TODO create an `Arbitrary[Nel[Nes[A]] @ Split]` or similar.
        implicit val _: Arbitrary[NonEmptyList[NonEmptySet[Int]]] = splitIn(parts, nes)

        forAll { res: NonEmptyList[NonEmptySet[Int]] =>
          if (parts <= 1 || parts > nes.size) {
            res should ===(NonEmptyList.of(nes))
          } else {
            (res.toList should have).length(parts.value.toLong)
            res.foldMap(_.toSortedSet) should ===(nes.toSortedSet)
          }
        }
      }
    }
  }
}
