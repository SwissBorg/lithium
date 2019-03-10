package akka.cluster.sbr

import cats.data.NonEmptySet
import org.scalatest.enablers.{Aggregating, Size}

import scala.collection.{GenTraversable, SortedMap}

object ScalaTestInstances {
  implicit def nonEmptySetSize[A]: Size[NonEmptySet[A]] = new Size[NonEmptySet[A]] {
    override def sizeOf(obj: NonEmptySet[A]): Long = obj.length
  }
}
