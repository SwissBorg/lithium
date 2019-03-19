package akka.cluster.sbr

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.cluster.sbr.strategies.keepmajority.KeepMajority
import akka.cluster.sbr.strategies.keepoldest.KeepOldest
import akka.cluster.sbr.strategies.keepreferee.KeepReferee
import akka.cluster.sbr.strategies.staticquorum.StaticQuorum
import akka.cluster.{Cluster, DowningProvider}
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

class DowningProviderImpl(system: ActorSystem) extends DowningProvider {
  import DowningProviderImpl._

  private val config = Config(system)

  override def downRemovalMargin: FiniteDuration = config.stableAfter

  override def downingActorProps: Option[Props] = {
    val keepMajority = Strategy.name[KeepMajority]
    val keepOldest   = Strategy.name[KeepOldest]
    val keepReferee  = Strategy.name[KeepReferee]
    val staticQuorum = Strategy.name[StaticQuorum]

    config.activeStrategy match {
      case `keepMajority` =>
        Strategy[KeepMajority]
          .fromConfig[KeepMajority.Config]
          .map(Downer.props(Cluster(system), _, config.stableAfter))
          .fold(err => throw new IllegalArgumentException(err.toString), _.some)

      case `keepOldest` =>
        Strategy[KeepOldest]
          .fromConfig[KeepOldest.Config]
          .map(Downer.props(Cluster(system), _, config.stableAfter))
          .fold(err => throw new IllegalArgumentException(err.toString), _.some)

      case `keepReferee` =>
        Strategy[KeepReferee]
          .fromConfig[KeepReferee.Config]
          .map(Downer.props(Cluster(system), _, config.stableAfter))
          .fold(err => throw new IllegalArgumentException(err.toString), _.some)

      case `staticQuorum` =>
        Strategy[StaticQuorum]
          .fromConfig[StaticQuorum.Config]
          .map(Downer.props(Cluster(system), _, config.stableAfter))
          .fold(err => throw new IllegalArgumentException(err.toString), _.some)

      case _ => None
    }
  }
}

object DowningProviderImpl {
  sealed abstract case class Config(activeStrategy: String, stableAfter: FiniteDuration, downAllWhenUnstable: FiniteDuration)

  object Config {
    // TODO handle errors
    def apply(system: ActorSystem): Config =
      new Config(
        system.settings.config.getString("akka.cluster.split-brain-resolver.active-strategy"),
        FiniteDuration(system.settings.config.getDuration("akka.cluster.split-brain-resolver.stable-after").toMillis,
                       TimeUnit.MILLISECONDS),
        FiniteDuration(system.settings.config.getDuration("akka.cluster.split-brain-resolver.down-all-when-unstable").toMillis,
          TimeUnit.MILLISECONDS)// TODO
      ) {}
  }
}
