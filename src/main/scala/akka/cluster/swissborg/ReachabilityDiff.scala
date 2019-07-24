package akka.cluster.swissborg

import akka.cluster._
import akka.cluster.swissborg.implicits._
import cats.implicits._

/**
  * The difference between two [[Reachability]] instances.
  */
final case class ReachabilityDiff(
    private val prevR: Option[Reachability],
    private val r: Reachability
) {

  /**
    * Attempt to get the [[Reachability.Record]] describing the unreachability detection of
    * `subject` by `observer`.
    */
  def findUnreachableRecord(
      observer: UniqueAddress,
      subject: UniqueAddress
  ): Option[Reachability.Record] =
    r.recordsFrom(observer)
      .find { r =>
        r.subject === subject && r.status === Reachability.Unreachable // find the record describing that `observer` sees `subject` as unreachable
      }

  /**
    * The set of observers grouped by unreachable member that have
    */
  lazy val newObserversGroupedByUnreachable: Map[UniqueAddress, Set[UniqueAddress]] = {
    prevR match {
      case Some(prevR) => r.filterRecords(!prevR.records.contains(_)).observersGroupedByUnreachable
      case None        => r.observersGroupedByUnreachable
    }
  }

  /**
    * Removes the records mentioning any of the `nodes`.
    */
  def remove(nodes: Set[UniqueAddress]): ReachabilityDiff =
    ReachabilityDiff(prevR.map(_.remove(nodes)), r.remove(nodes))
}
