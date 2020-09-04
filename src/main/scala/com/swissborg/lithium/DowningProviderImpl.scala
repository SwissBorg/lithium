package com.swissborg.lithium

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.cluster.{Cluster, DowningProvider}
import akka.event.Logging
import cats.effect.SyncIO
import cats.syntax.all._
import com.swissborg.lithium
import com.swissborg.lithium.resolver.SplitBrainResolver
import com.swissborg.lithium.strategy.Strategy
import com.swissborg.lithium.strategy._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * Implementation of a DowningProvider building a [[SplitBrainResolver]].
 *
 * @param system the current actor system.
 */
class DowningProviderImpl(system: ActorSystem) extends DowningProvider {

  import DowningProviderImpl._

  private val config = Config(system)
  private val logger = Logging(system, this.getClass)

  override def downRemovalMargin: FiniteDuration = config.stableAfter

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def downingActorProps: Option[Props] = {
    val keepMajority = KeepMajorityConfig.name
    val keepOldest   = KeepOldestConfig.name
    val keepReferee  = KeepRefereeConfig.name
    val staticQuorum = StaticQuorumConfig.name
    val downAll      = DownAll.name

    def sbResolver(strategy: Strategy[SyncIO]): Props =
      SplitBrainResolver.props(strategy,
                               config.stableAfter,
                               config.downAllWhenUnstable,
                               config.trackIndirectlyConnectedNodes)

    config.activeStrategy match {
      case `keepMajority` =>
        val config = KeepMajorityConfig.fromConfig(system.settings.config)
        logStartup(keepMajority)
        sbResolver(new lithium.strategy.KeepMajority(config, Cluster(system).settings.AllowWeaklyUpMembers)).some

      case `keepOldest` =>
        val config = KeepOldestConfig.fromConfig(system.settings.config)
        logStartup(keepOldest)
        sbResolver(new lithium.strategy.KeepOldest(config)).some

      case `keepReferee` =>
        val config = KeepRefereeConfig.fromConfig(system.settings.config)
        logStartup(keepReferee)
        sbResolver(new lithium.strategy.KeepReferee(config)).some

      case `staticQuorum` =>
        val config = StaticQuorumConfig.fromConfig(system.settings.config)
        logStartup(staticQuorum)
        sbResolver(new lithium.strategy.StaticQuorum(config)).some

      case `downAll` =>
        logStartup(downAll)
        sbResolver(new lithium.strategy.DownAll).some

      case unknownStrategy =>
        val err = new IllegalArgumentException(
          s"Unknown strategy: '${unknownStrategy}'. Expected one off: ${StaticQuorumConfig.name}, ${KeepMajorityConfig.name}, ${KeepOldestConfig.name} or ${KeepRefereeConfig.name}"
        )

        logger.error(err, "Invalid strategy")
        throw err
    }
  }

  private def logStartup(strategyName: String): Unit =
    logger.info("Starting Lithium with the {} strategy.", strategyName)
}

object DowningProviderImpl {

  sealed abstract case class Config(activeStrategy: String,
                                    stableAfter: FiniteDuration,
                                    downAllWhenUnstable: Option[FiniteDuration],
                                    trackIndirectlyConnectedNodes: Boolean)

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

      val stableAfter =
        FiniteDuration(system.settings.config.getDuration(stableAfterPath).toMillis, TimeUnit.MILLISECONDS)

      // 'down-all-when-unstable' config when undefined is derived from 'stable-after'.
      // Otherwise it must be a duration or set to 'off'.
      val downAllWhenUnstable =
        if (system.settings.config.hasPath(downAllWhenUnstablePath)) {
          val readAsDuration = Try(
            Some(
              FiniteDuration(system.settings.config.getDuration(downAllWhenUnstablePath).toMillis,
                             TimeUnit.MILLISECONDS)
            )
          )

          val readAsBoolean =
            Try(system.settings.config.getBoolean(downAllWhenUnstablePath)).flatMap { b =>
              if (b) {
                Failure(new IllegalArgumentException("'down-all-when-unstable' must be a duration or set to 'off'."))
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
