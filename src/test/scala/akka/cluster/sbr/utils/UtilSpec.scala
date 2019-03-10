package akka.cluster.sbr.utils

import cats.Order
import cats.data.{NonEmptyList, NonEmptySet}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._
import org.scalacheck.Prop._
import org.scalacheck.Properties
import cats.implicits._

import scala.collection.immutable.SortedSet

class UtilSpec extends Properties("Util") {
  implicit val intOrder: Order[Int] = Order.fromOrdering
  implicit def nonEmptySetOrdering[A: Order]: Order[NonEmptySet[A]] = new Order[NonEmptySet[A]] {
    override def compare(x: NonEmptySet[A], y: NonEmptySet[A]): Int = ???
  }

  property("splitInt") = forAll { (parts: Long Refined Positive, head: Int, tail: SortedSet[Int]) =>
    val nes = NonEmptySet(head, tail)

    implicit val _ = splitIn(parts, nes)

    forAll { res: NonEmptyList[NonEmptySet[Int]] =>
      if (parts.value >= 0 && parts.value <= (tail.size + 1)) {
        res.size == parts.value && // expected number of parts
        res.foldMap(_.toSortedSet).diff(nes.toSortedSet).isEmpty
      } else res.head == nes
    }
  }
}
