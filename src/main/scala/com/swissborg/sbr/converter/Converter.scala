package com.swissborg.sbr
package converter

import akka.actor._
import akka.cluster.swissborg.ConverterActor

/**
  * @see [[ConverterImpl]]
  */
private[sbr] object Converter extends ExtensionId[ConverterImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ConverterImpl = {
    discard(system.actorOf(ConverterActor.props))
    new ConverterImpl(system)
  }

  override def lookup(): ExtensionId[_ <: Extension] = Converter
}
