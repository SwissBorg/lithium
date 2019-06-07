package com.swissborg.sbr

import akka.actor.{ActorRef, ActorSystem, Extension}

class ConverterImpl(system: ActorSystem) extends Extension {
  def subscribeToSeenChanged(actor: ActorRef): Boolean =
    system.eventStream.subscribe(actor, classOf[SBSeenChanged])

  def subscribeToReachabilityChanged(actor: ActorRef): Boolean =
    system.eventStream.subscribe(actor, classOf[SBReachabilityChanged])

  def unsubscribe(actor: ActorRef): Unit = system.eventStream.unsubscribe(actor)
}
