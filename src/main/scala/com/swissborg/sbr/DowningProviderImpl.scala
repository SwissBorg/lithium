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
    val keepMajority = KeepMajority.name
    val keepOldest   = KeepOldest.name
    val keepReferee  = KeepReferee.name
    val staticQuorum = StaticQuorum.name
    val downAll      = DownAll.name

    val strategy = config.activeStrategy match {
      case `keepMajority`  => KeepMajority.load.map(SBResolver.props(_, config.stableAfter))
      case `keepOldest`    => KeepOldest.load.map(SBResolver.props(_, config.stableAfter))
      case `keepReferee`   => KeepReferee.load.map(SBResolver.props(_, config.stableAfter))
      case `staticQuorum`  => StaticQuorum.load.map(SBResolver.props(_, config.stableAfter))
      case `downAll`       => SBResolver.props(DownAll(), config.stableAfter).asRight
      case unknownStrategy => UnknownStrategy(unknownStrategy).asLeft
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
