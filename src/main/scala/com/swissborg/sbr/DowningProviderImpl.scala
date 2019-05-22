package com.swissborg.sbr

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.cluster.DowningProvider
import cats.implicits._
import com.swissborg.sbr.resolver.SBResolver
import com.swissborg.sbr.strategies.downall.DownAll
import com.swissborg.sbr.strategies.keepmajority.KeepMajority
import com.swissborg.sbr.strategies.keepoldest.KeepOldest
import com.swissborg.sbr.strategies.keepreferee.KeepReferee
import com.swissborg.sbr.strategies.staticquorum.StaticQuorum
import com.swissborg.sbr.strategy.StrategyReader.UnknownStrategy
import eu.timepit.refined.pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.duration.FiniteDuration

/**
 * Implementation of a DowningProvider building a [[SBResolver]].
 *
 * @param system the current actor system.
 */
class DowningProviderImpl(system: ActorSystem) extends DowningProvider {
  import DowningProviderImpl._

  private val config = Config(system)

  override def downRemovalMargin: FiniteDuration = config.stableAfter

  override def downingActorProps: Option[Props] = {
    val keepMajority = KeepMajority.Config.name
    val keepOldest   = KeepOldest.Config.name
    val keepReferee  = KeepReferee.Config.name
    val staticQuorum = StaticQuorum.Config.name
    val downAll      = DownAll.name

    val strategy = config.activeStrategy match {
      case `keepMajority` =>
        KeepMajority.Config.load.map(c => SBResolver.props(new KeepMajority(c), config.stableAfter))

      case `keepOldest` =>
        KeepOldest.Config.load.map(c => SBResolver.props(new KeepOldest(c), config.stableAfter))

      case `keepReferee` =>
        KeepReferee.Config.load.map(c => SBResolver.props(new KeepReferee(c), config.stableAfter))

      case `staticQuorum` =>
        StaticQuorum.Config.load.map(c => SBResolver.props(new StaticQuorum(c), config.stableAfter))

      case `downAll` =>
        SBResolver.props(new DownAll, config.stableAfter).asRight

      case unknownStrategy =>
        UnknownStrategy(unknownStrategy).asLeft
    }

    strategy.toTry.get.some
  }
}

object DowningProviderImpl {
  sealed abstract case class Config(activeStrategy: String, stableAfter: FiniteDuration)

  object Config {
    // TODO handle errors
    def apply(system: ActorSystem): Config =
      new Config(
        system.settings.config.getString("com.swissborg.sbr.active-strategy"),
        FiniteDuration(system.settings.config.getDuration("com.swissborg.sbr.stable-after").toMillis,
                       TimeUnit.MILLISECONDS)
//        FiniteDuration(
//          system.settings.config.getDuration("akka.cluster.split-brain-resolver.down-all-when-unstable").toMillis,
//          TimeUnit.MILLISECONDS
//        ) // TODO
      ) {}
  }
}
