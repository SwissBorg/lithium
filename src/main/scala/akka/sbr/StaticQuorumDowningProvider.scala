package akka.sbr

import akka.actor.{ActorSystem, Props}
import akka.cluster.DowningProvider

import scala.concurrent.duration.FiniteDuration

class StaticQuorumDowningProvider(system: ActorSystem) extends DowningProvider {
  override def downRemovalMargin: FiniteDuration = ???
  override def downingActorProps: Option[Props] = ???
}
