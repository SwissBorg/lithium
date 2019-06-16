package com.swissborg.sbr

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.cluster.DowningProvider
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr.resolver.SBResolver
import com.swissborg.sbr.strategy.Strategy
import com.swissborg.sbr.strategy.StrategyReader.UnknownStrategy
import com.swissborg.sbr.strategy.downall.DownAll
import com.swissborg.sbr.strategy.keepmajority.KeepMajority
import com.swissborg.sbr.strategy.keepoldest.KeepOldest
import com.swissborg.sbr.strategy.keepreferee.KeepReferee
import com.swissborg.sbr.strategy.staticquorum.StaticQuorum
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
    val keepOldest = KeepOldest.Config.name
    val keepReferee = KeepReferee.Config.name
    val staticQuorum = StaticQuorum.Config.name
    val downAll = DownAll.name

    def sbResolver(strategy: Strategy[SyncIO]): Props =
      SBResolver.props(strategy, config.stableAfter, config.downAllWhenUnstable)

    val strategy = config.activeStrategy match {
      case `keepMajority`  => KeepMajority.Config.load.map(c => sbResolver(new KeepMajority(c)))
      case `keepOldest`    => KeepOldest.Config.load.map(c => sbResolver(new KeepOldest(c)))
      case `keepReferee`   => KeepReferee.Config.load.map(c => sbResolver(new KeepReferee(c)))
      case `staticQuorum`  => StaticQuorum.Config.load.map(c => sbResolver(new StaticQuorum(c)))
      case `downAll`       => sbResolver(new DownAll).asRight
      case unknownStrategy => UnknownStrategy(unknownStrategy).asLeft
    }

    strategy.toTry.get.some
  }
}

object DowningProviderImpl {
  sealed abstract case class Config(
      activeStrategy: String,
      stableAfter: FiniteDuration,
      downAllWhenUnstable: Boolean
  )

  object Config {
    // TODO handle errors
    def apply(system: ActorSystem): Config =
      new Config(
        system.settings.config.getString("com.swissborg.sbr.active-strategy"),
        FiniteDuration(
          system.settings.config.getDuration("com.swissborg.sbr.stable-after").toMillis,
          TimeUnit.MILLISECONDS
        ),
        system.settings.config.getBoolean("com.swissborg.sbr.down-all-when-unstable")
      ) {}
  }
}
