package akka.cluster.swissborg

import akka.cluster.swissborg.implicits._
import akka.cluster.{Reachability, UniqueAddress}
import cats.implicits._

final case class SBReachability(private val r: Reachability) {
  def findUnreachableRecord(
      observer: UniqueAddress,
      subject: UniqueAddress
  ): Option[Reachability.Record] =
    r.recordsFrom(observer)
      .find { r =>
        r.subject === subject && r.status === Reachability.Unreachable // find the record describing that `observer` sees `subject` as unreachable
      }

  def observersGroupedByUnreachable: Map[UniqueAddress, Set[UniqueAddress]] =
    r.observersGroupedByUnreachable
}
