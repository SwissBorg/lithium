package akka.cluster.swissborg

import akka.cluster._
import akka.cluster.swissborg.implicits._
import cats.implicits._

/**
  * Newtype of [[Reachability]] so it can be used by actors in other
  * namespaces than `akka.cluster`
  */
final case class SBReachability(private val r: Reachability) extends AnyVal {

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
    * @see [[Reachability.observersGroupedByUnreachable]]
    */
  def observersGroupedByUnreachable: Map[UniqueAddress, Set[UniqueAddress]] =
    r.observersGroupedByUnreachable

  /**
    * @see [[Reachability.remove()]]
    */
  def remove(nodes: Set[UniqueAddress]): SBReachability = SBReachability(r.remove(nodes))
}
