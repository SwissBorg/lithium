package com.swissborg.sbr
package reachability

import cats.Eq

sealed abstract class SBReachabilityStatus

object SBReachabilityStatus {
  final case object Reachable extends SBReachabilityStatus with SBSelfReachabilityStatus
  final case object Unreachable extends SBReachabilityStatus
  final case object IndirectlyConnected extends SBReachabilityStatus with SBSelfReachabilityStatus

  implicit val sbReachabilityStatusEq: Eq[SBReachabilityStatus] = Eq.fromUniversalEquals
}

// Marker trait.
// So that the "current" node cannot be unreachable.
sealed trait SBSelfReachabilityStatus
