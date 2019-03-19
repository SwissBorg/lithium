package akka.cluster.sbr

import akka.actor.{ActorSystem, Props}
import akka.cluster.sbr.strategies.keepmajority.KeepMajority
import akka.cluster.sbr.strategies.keepoldest.KeepOldest
import akka.cluster.sbr.strategies.keepreferee.KeepReferee
import akka.cluster.sbr.strategies.staticquorum.StaticQuorum
import akka.cluster.{Cluster, DowningProvider}
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

class DowningProviderImpl(system: ActorSystem) extends DowningProvider {
  private val config         = system.settings.config
  private val activeStrategy = config.getString("akka.cluster.split-brain-resolver.active-strategy")

  override def downRemovalMargin: FiniteDuration = FiniteDuration(5, "seconds")
  // config.stableAfter

  override def downingActorProps: Option[Props] = {
    val keepMajority = Strategy.name[KeepMajority]
    val keepOldest   = Strategy.name[KeepOldest]
    val keepReferee  = Strategy.name[KeepReferee]
    val staticQuorum = Strategy.name[StaticQuorum]

    activeStrategy match {
      case `keepMajority` =>
        Strategy[KeepMajority]
          .fromConfig[KeepMajority.Config]
          .map(Downer.props(Cluster(system), _, FiniteDuration(5, "seconds")))
          .fold(err => throw new IllegalArgumentException(err.toString), _.some)

      case `keepOldest` =>
        Strategy[KeepOldest]
          .fromConfig[KeepOldest.Config]
          .map(Downer.props(Cluster(system), _, FiniteDuration(5, "seconds")))
          .fold(err => throw new IllegalArgumentException(err.toString), _.some)

      case `keepReferee` =>
        Strategy[KeepReferee]
          .fromConfig[KeepReferee.Config]
          .map(Downer.props(Cluster(system), _, FiniteDuration(5, "seconds")))
          .fold(err => throw new IllegalArgumentException(err.toString), _.some)

      case `staticQuorum` =>
        val a = Strategy[StaticQuorum]
          .fromConfig[StaticQuorum.Config]

        a.map(Downer.props(Cluster(system), _, FiniteDuration(5, "seconds")))
          .fold(err => throw new IllegalArgumentException(err.toString), _.some)

      case _ => None
    }
  }
}

object DowningProviderImpl {
  final case class Config private (activeStrategy: String)

  object Config {
    def apply(system: ActorSystem) = ???
  }
}
