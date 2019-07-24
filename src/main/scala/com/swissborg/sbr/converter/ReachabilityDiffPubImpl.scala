package com.swissborg.sbr
package converter

import akka.actor._

/**
  * Extension to subscribe to [[ReachabilityDiffChanged]] events.
  */
private[sbr] class ReachabilityDiffPubImpl(private val system: ActorSystem) extends Extension {
  def subscribeToReachabilityChanged(actor: ActorRef): Boolean =
    system.eventStream.subscribe(actor, classOf[ReachabilityDiffChanged])

  def unsubscribe(actor: ActorRef): Unit = system.eventStream.unsubscribe(actor)
}
