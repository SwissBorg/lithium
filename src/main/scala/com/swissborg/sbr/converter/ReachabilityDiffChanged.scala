package com.swissborg.sbr
package converter

import akka.cluster.swissborg._

/**
  * Mirror of [[akka.cluster.ClusterEvent.ReachabilityChanged]]
  * so it can be subscribed to by actors in other packages.
  */
final case class ReachabilityDiffChanged(reachabilityDiff: ReachabilityDiff)
