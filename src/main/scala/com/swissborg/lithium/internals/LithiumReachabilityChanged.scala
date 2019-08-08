package com.swissborg.lithium

package internals

import akka.cluster.swissborg._

/**
  * Mirror of [[akka.cluster.ClusterEvent.ReachabilityChanged]]
  * so it can be subscribed to by actors in other packages.
  */
final case class LithiumReachabilityChanged(reachability: LithiumReachability)
