package com.swissborg.sbr

import akka.actor.{ActorPath, Address}

object Util {

  /**
   * Return the path to the actor on the node at `address` with the given
   * path without the original address.
   */
  def pathAtAddress(address: Address, path: ActorPath): ActorPath =
    ActorPath.fromString(s"${address.toString}/${path.toStringWithoutAddress}")
}
