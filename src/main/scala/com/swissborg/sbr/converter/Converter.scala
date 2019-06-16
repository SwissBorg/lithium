package com.swissborg.sbr.converter

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.cluster.swissborg.ConverterActor

private[sbr] object Converter extends ExtensionId[ConverterImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ConverterImpl = {
    system.actorOf(ConverterActor.props)
    new ConverterImpl(system)
  }

  override def lookup(): ExtensionId[_ <: Extension] = Converter
}
