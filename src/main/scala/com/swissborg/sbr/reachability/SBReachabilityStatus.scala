package com.swissborg.sbr
package reachability

sealed abstract class SBReachabilityStatus

object SBReachabilityStatus {
  final case object Reachable extends SBReachabilityStatus with SBSelfReachabilityStatus
  final case object Unreachable extends SBReachabilityStatus
  final case object IndirectlyConnected extends SBReachabilityStatus with SBSelfReachabilityStatus
}

// Marker trait.
// So that the "current" node cannot be unreachable.
sealed trait SBSelfReachabilityStatus
