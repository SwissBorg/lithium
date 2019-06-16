package com.swissborg.sbr.converter

import akka.actor.{ActorRef, ActorSystem, Extension}
import com.swissborg.sbr.reachability.SBReachabilityReporter.SBReachabilityChanged

private[sbr] class ConverterImpl(system: ActorSystem) extends Extension {
  def subscribeToReachabilityChanged(actor: ActorRef): Boolean =
    system.eventStream.subscribe(actor, classOf[SBReachabilityChanged])

  def unsubscribe(actor: ActorRef): Unit = system.eventStream.unsubscribe(actor)
}
