package com.swissborg.lithium

package internals

import akka.actor._
import akka.cluster.swissborg.ClusterInternalsPublisher

/**
 * @see [[ClusterInternalsImpl]]
 */
private[lithium] object ClusterInternals extends ExtensionId[ClusterInternalsImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ClusterInternalsImpl = {
    system.systemActorOf(ClusterInternalsPublisher.props, "cluster_internals")
    new ClusterInternalsImpl(system)
  }

  override def lookup(): ExtensionId[_ <: Extension] = ClusterInternals
}
