package akka.cluster.sbr

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.cluster.sbr.strategies.downall.DownAll
import akka.cluster.sbr.strategies.keepmajority.KeepMajority
import akka.cluster.sbr.strategies.keepoldest.KeepOldest
import akka.cluster.sbr.strategies.keepreferee.KeepReferee
import akka.cluster.sbr.strategies.staticquorum.StaticQuorum
import akka.cluster.sbr.strategy.StrategyReader.UnknownStrategy
import akka.cluster.{Cluster, DowningProvider}
import cats.implicits._
import eu.timepit.refined.pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.duration.FiniteDuration

class DowningProviderImpl(system: ActorSystem) extends DowningProvider {
  import DowningProviderImpl._

  private val config = Config(system)

  override def downRemovalMargin: FiniteDuration = config.stableAfter

  override def downingActorProps: Option[Props] = {
    val keepMajority = KeepMajority.name
    val keepOldest   = KeepOldest.name
    val keepReferee  = KeepReferee.name
    val staticQuorum = StaticQuorum.name
    val downAll      = DownAll.name

    val strategy = config.activeStrategy match {
      case `keepMajority` =>
        KeepMajority.load
          .map(SBResolver.props(_, config.stableAfter, config.downAllWhenUnstable))

      case `keepOldest` =>
        KeepOldest.load
          .map(SBResolver.props(_, config.stableAfter, config.downAllWhenUnstable))

      case `keepReferee` =>
        KeepReferee.load
          .map(SBResolver.props(_, config.stableAfter, config.downAllWhenUnstable))

      case `staticQuorum` =>
        StaticQuorum.load
          .map(SBResolver.props(_, config.stableAfter, config.downAllWhenUnstable))

      case `downAll` =>
        SBResolver.props(DownAll(), config.stableAfter, config.downAllWhenUnstable).asRight

      case unknownStrategy => UnknownStrategy(unknownStrategy).asLeft
    }

    strategy.toTry.get.some
  }
}

object DowningProviderImpl {
  sealed abstract case class Config(activeStrategy: String,
                                    stableAfter: FiniteDuration,
                                    downAllWhenUnstable: FiniteDuration)

  object Config {
    // TODO handle errors
    def apply(system: ActorSystem): Config =
      new Config(
        system.settings.config.getString("akka.cluster.split-brain-resolver.active-strategy"),
        FiniteDuration(system.settings.config.getDuration("akka.cluster.split-brain-resolver.stable-after").toMillis,
                       TimeUnit.MILLISECONDS),
        FiniteDuration(
          system.settings.config.getDuration("akka.cluster.split-brain-resolver.down-all-when-unstable").toMillis,
          TimeUnit.MILLISECONDS
        ) // TODO
      ) {}
  }
}
