package akka.cluster.sbr

import akka.actor.{ActorSystem, Props}
import akka.cluster.{Cluster, DowningProvider}
import akka.cluster.sbr.strategies.keepmajority.KeepMajority
import akka.cluster.sbr.strategies.keepoldest.KeepOldest
import akka.cluster.sbr.strategies.keepreferee.KeepReferee
import akka.cluster.sbr.strategies.staticquorum.StaticQuorum
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

class DowningProviderImpl(system: ActorSystem) extends DowningProvider {
  override def downRemovalMargin: FiniteDuration = ???
  override def downingActorProps: Option[Props] = {
    val strategy: String = ???

    val keepMajority = Strategy.name[KeepMajority]
    val keepOldest = Strategy.name[KeepOldest]
    val keepReferee = Strategy.name[KeepReferee]
    val staticQuorum = Strategy.name[StaticQuorum]

    strategy match {
      case `keepMajority` =>
        Strategy[KeepMajority].unit
          .map(Downer.props(Cluster(system), _, FiniteDuration(0, "seconds")))
          .fold(throw new IllegalArgumentException("bla"), _.some)

      case `keepOldest` =>
        Strategy[KeepOldest]
          .fromConfig[KeepOldest.Config]
          .map(Downer.props(Cluster(system), _, FiniteDuration(0, "seconds")))
          .fold(throw new IllegalArgumentException("bla"), _.some)

      case `keepReferee` =>
        Strategy[KeepReferee]
          .fromConfig[KeepReferee.Config]
          .map(Downer.props(Cluster(system), _, FiniteDuration(0, "seconds")))
          .fold(throw new IllegalArgumentException("bla"), _.some)

      case `staticQuorum` =>
        Strategy[StaticQuorum]
          .fromConfig[StaticQuorum.Config]
          .map(Downer.props(Cluster(system), _, FiniteDuration(0, "seconds")))
          .fold(throw new IllegalArgumentException("bla"), _.some)

      case _ => None
    }
  }
}
