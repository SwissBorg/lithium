package com.swissborg.sbr
package converter

import akka.actor._
import akka.cluster.swissborg.ReachabilityDiffPublisher

/**
  * @see [[ReachabilityDiffPubImpl]]
  */
private[sbr] object ReachabilityDiffPub
    extends ExtensionId[ReachabilityDiffPubImpl]
    with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ReachabilityDiffPubImpl = {
    discard(system.actorOf(ReachabilityDiffPublisher.props))
    new ReachabilityDiffPubImpl(system)
  }

  override def lookup(): ExtensionId[_ <: Extension] = ReachabilityDiffPub
}
