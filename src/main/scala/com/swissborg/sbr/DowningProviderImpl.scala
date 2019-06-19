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

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

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
      downAllWhenUnstable: Option[FiniteDuration]
  )

  object Config {
    private final val activeStrategyPath: String = "com.swissborg.sbr.active-strategy"
    private final val stableAfterPath: String = "com.swissborg.sbr.stable-after"
    private final val downAllWhenUnstablePath: String = "com.swissborg.sbr.down-all-when-unstable"

    // TODO handle errors
    def apply(system: ActorSystem): Config = {
      val activeStrategy = system.settings.config.getString(activeStrategyPath)

      val stableAfter = FiniteDuration(
        system.settings.config.getDuration(stableAfterPath).toMillis,
        TimeUnit.MILLISECONDS
      )

      // 'down-all-when-unstable' config when undefined is derived from 'stable-after'.
      // Otherwise it must be a duration or set to 'off'.
      val downAllWhenUnstable =
        if (system.settings.config.hasPath(downAllWhenUnstablePath)) {
          val readAsDuration = Try(
            Some(
              FiniteDuration(
                system.settings.config.getDuration(downAllWhenUnstablePath).toMillis,
                TimeUnit.MILLISECONDS
              )
            )
          )

          val readAsBoolean =
            Try(system.settings.config.getBoolean(downAllWhenUnstablePath)).flatMap { b =>
              if (b) {
                Failure(
                  new IllegalArgumentException(
                    "'down-all-when-unstable' must be a duration or set to 'off'."
                  )
                )
              } else {
                Success(None)
              }
            }

          readAsDuration.orElse(readAsBoolean).get
        } else {
          // Default
          Some(stableAfter + (stableAfter.toMillis * 0.75).millis)
        }

      new Config(activeStrategy, stableAfter, downAllWhenUnstable) {}
    }
  }
}
