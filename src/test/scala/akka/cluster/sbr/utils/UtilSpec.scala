package akka.cluster.sbr.utils

import akka.cluster.sbr.MySpec
import cats.Order
import cats.data.{NonEmptyList, NonEmptySet}
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._
import org.scalacheck.Arbitrary

import scala.collection.immutable.SortedSet

class UtilSpec extends MySpec {
  implicit val intOrder: Order[Int] = Order.fromOrdering

  "Util" - {
    "1 - splitIn" in {
      forAll { (parts: Int Refined Positive, head: Int, tail: SortedSet[Int]) =>
        val nes = NonEmptySet(head, tail)

        implicit val _: Arbitrary[NonEmptyList[NonEmptySet[Int]]] = splitIn(parts, nes)

        forAll { res: NonEmptyList[NonEmptySet[Int]] =>
          if (parts.value >= 0 && parts.value <= (tail.size + 1)) {
            (res.toList should have).size(parts.value)
            res.foldMap(_.toSortedSet) shouldEqual nes.toSortedSet
          } else res shouldEqual NonEmptyList.of(nes)
        }
      }
    }
  }
}
