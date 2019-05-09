package akka.cluster.sbr

import akka.actor.{ActorPath, Address}

object Util {
  def pathAtAddress(address: Address, path: ActorPath): ActorPath =
    ActorPath.fromString(s"${address.toString}/${path.toStringWithoutAddress}")
}
