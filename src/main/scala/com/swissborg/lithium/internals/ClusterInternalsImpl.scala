package com.swissborg.lithium

package internals

import akka.actor._

private[lithium] class ClusterInternalsImpl(private val system: ActorSystem) extends Extension {

  def subscribeToReachabilityChanged(actor: ActorRef): Boolean =
    system.eventStream.subscribe(actor, classOf[LithiumReachabilityChanged])

  def subscribeToSeenChanged(actor: ActorRef): Boolean =
    system.eventStream.subscribe(actor, classOf[LithiumSeenChanged])

  def unsubscribe(actor: ActorRef): Unit = system.eventStream.unsubscribe(actor)
}
