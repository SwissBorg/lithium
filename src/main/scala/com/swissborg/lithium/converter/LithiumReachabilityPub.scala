package com.swissborg.lithium

package converter

import akka.actor._
import akka.cluster.swissborg.LithiumReachabilityPublisher

/**
  * @see [[LithiumReachabilityPubImpl]]
  */
private[lithium] object LithiumReachabilityPub
    extends ExtensionId[LithiumReachabilityPubImpl]
    with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): LithiumReachabilityPubImpl = {
    discard(system.actorOf(LithiumReachabilityPublisher.props))
    new LithiumReachabilityPubImpl(system)
  }

  override def lookup(): ExtensionId[_ <: Extension] = LithiumReachabilityPub
}
