package com.swissborg.lithium

package converter

import akka.actor._

/**
  * Extension to subscribe to [[LithiumReachabilityChanged]] events.
  */
private[lithium] class LithiumReachabilityPubImpl(private val system: ActorSystem) extends Extension {

  def subscribeToReachabilityChanged(actor: ActorRef): Boolean =
    system.eventStream.subscribe(actor, classOf[LithiumReachabilityChanged])

  def unsubscribe(actor: ActorRef): Unit = system.eventStream.unsubscribe(actor)
}
