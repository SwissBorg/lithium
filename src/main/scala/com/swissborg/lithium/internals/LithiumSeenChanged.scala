package com.swissborg.lithium

package internals

import akka.actor.Address

/**
 * Mirror of [[akka.cluster.ClusterEvent.SeenChanged]]
 * so it can be subscribed to by actors in other packages.
 */
final case class LithiumSeenChanged(convergence: Boolean, seenBy: Set[Address])
