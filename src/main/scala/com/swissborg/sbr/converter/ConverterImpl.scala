package com.swissborg.sbr
package converter

import akka.actor._

/**
  * Extension to subscribe to [[SBReachabilityChanged]] events.
  */
private[sbr] class ConverterImpl(private val system: ActorSystem) extends Extension {
  def subscribeToReachabilityChanged(actor: ActorRef): Boolean =
    system.eventStream.subscribe(actor, classOf[SBReachabilityChanged])

  def unsubscribe(actor: ActorRef): Unit = system.eventStream.unsubscribe(actor)
}
