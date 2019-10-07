package com.swissborg.lithium

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.cluster.DowningProvider
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.lithium
import com.swissborg.lithium.resolver.SplitBrainResolver
import com.swissborg.lithium.strategy.Strategy
import com.swissborg.lithium.strategy.StrategyReader.UnknownStrategy
import com.swissborg.lithium.strategy._
import com.typesafe.scalalogging.LazyLogging
import eu.timepit.refined.pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Implementation of a DowningProvider building a [[SplitBrainResolver]].
  *
  * @param system the current actor system.
  */
class DowningProviderImpl(system: ActorSystem) extends DowningProvider with LazyLogging {

  import DowningProviderImpl._

  private val config = Config(system)

  override def downRemovalMargin: FiniteDuration = config.stableAfter

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def downingActorProps: Option[Props] = {
    val keepMajority = KeepMajority.Config.name
    val keepOldest   = KeepOldest.Config.name
    val keepReferee  = KeepReferee.Config.name
    val staticQuorum = StaticQuorum.Config.name
    val downAll      = DownAll.name

    def sbResolver(strategy: Strategy[SyncIO]): Props =
      SplitBrainResolver.props(
        strategy,
        config.stableAfter,
        config.downAllWhenUnstable,
        config.trackIndirectlyConnectdeNodes
      )

    val strategy = config.activeStrategy match {
      case `keepMajority` =>
        KeepMajority.Config
          .load(system.settings.config)
          .map { config =>
            logStartup(keepMajority)
            sbResolver(new lithium.strategy.KeepMajority(config))
          }

      case `keepOldest` =>
        KeepOldest.Config
          .load(system.settings.config)
          .map { config =>
            logStartup(keepOldest)
            sbResolver(new lithium.strategy.KeepOldest(config))
          }

      case `keepReferee` =>
        KeepReferee.Config
          .load(system.settings.config)
          .map { config =>
            logStartup(keepReferee)
            sbResolver(new lithium.strategy.KeepReferee(config))
          }

      case `staticQuorum` =>
        StaticQuorum.Config
          .load(system.settings.config)
          .map { config =>
            logStartup(staticQuorum)
            sbResolver(new lithium.strategy.StaticQuorum(config))
          }

      case `downAll` =>
        logStartup(downAll)
        sbResolver(new lithium.strategy.DownAll).asRight

      case unknownStrategy =>
        logger.error(s"'$unknownStrategy' is not a valid Lithium strategy.")
        UnknownStrategy(unknownStrategy).asLeft
    }

    strategy.fold(throw _, Some(_))
  }

  private def logStartup(strategyName: String): Unit =
    logger.info(s"Starting Lithium with the $strategyName strategy.")
}

object DowningProviderImpl {

  sealed abstract case class Config(
      activeStrategy: String,
      stableAfter: FiniteDuration,
      downAllWhenUnstable: Option[FiniteDuration],
      trackIndirectlyConnectdeNodes: Boolean
  )

  object Config {
    final private val prefix: String                  = "com.swissborg.lithium"
    final private val activeStrategyPath: String      = s"$prefix.active-strategy"
    final private val stableAfterPath: String         = s"$prefix.stable-after"
    final private val downAllWhenUnstablePath: String = s"$prefix.down-all-when-unstable"
    final private val trackIndirectlyConnectedNodesPath: String =
      s"$prefix.track-indirectly-connected"

    // TODO handle errors
    @SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
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

      val trackIndirectlyConnectedNodes =
        system.settings.config.getBoolean(trackIndirectlyConnectedNodesPath)

      new Config(activeStrategy, stableAfter, downAllWhenUnstable, trackIndirectlyConnectedNodes) {}
    }
  }

}
