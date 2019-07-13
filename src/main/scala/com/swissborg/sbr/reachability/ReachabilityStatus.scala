package com.swissborg.sbr
package reachability

import cats.Eq

sealed abstract class ReachabilityStatus

object ReachabilityStatus {
  final case object Reachable extends SelfReachabilityStatus
  final case object Unreachable extends ReachabilityStatus
  final case object IndirectlyConnected extends SelfReachabilityStatus

  implicit val sbReachabilityStatusEq: Eq[ReachabilityStatus] = Eq.fromUniversalEquals
}

// Marker trait.
// So that the "current" node cannot be unreachable.
sealed trait SelfReachabilityStatus extends ReachabilityStatus
