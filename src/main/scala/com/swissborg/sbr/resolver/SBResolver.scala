package com.swissborg.sbr
package resolver

import akka.actor._
import akka.cluster.Cluster
import akka.cluster._
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.splitbrain._
import com.swissborg.sbr.strategy._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._

import scala.concurrent.duration._

/**
  * Actor resolving split-brain scenarios.
  *
  * It handles two events: `SBResolver.HandleSplitBrain` and `SBResolver.DownAll`,
  * both accompagnied with a `WorldView` describing a partitioned cluster.
  *
  * The `SBResolver.HandleSplitBrain` event triggers the downing of the members
  * as described by the decision given by `Union(_strategy, IndirectlyConnected)`.
  * All but the joining/weakly-up members in the cluster will run the strategy.
  * Joining/weakly-up members will not down the nodes as their world view might
  * be wrong. It can happen that they did not see all the reachability contentions
  * and noted some nodes as unreachable that are in fact indirectly-connected.
  *
  * The `SBResolver.DownAll` event triggers the downing of all the nodes. In contrast
  * to the event above, joining/weakly-up will also down nodes. To down all the nodes
  * the only information is needed are all the cluster members.
  *
  * @param _strategy the strategy with which to resolved the split-brain.
  * @param stableAfter duration during which a cluster has to be stable before attempting to resolve a split-brain.
  * @param downAllWhenUnstable down the partition if the cluster has been unstable for longer than `stableAfter + 3/4 * stableAfter`.
  */
private[sbr] class SBResolver(
    private val _strategy: Strategy[SyncIO],
    private val stableAfter: FiniteDuration,
    private val downAllWhenUnstable: Option[FiniteDuration]
) extends Actor
    with ActorLogging {
  discard(
    context.actorOf(
      SBSplitBrainReporter.props(self, stableAfter, downAllWhenUnstable),
      "splitbrain-reporter"
    )
  )

  private val cluster: Cluster = Cluster(context.system)

  private val strategy: Union[SyncIO, Strategy, IndirectlyConnected] =
    new Union(_strategy, new IndirectlyConnected)

  private val downAll: DownAll[SyncIO] = new DownAll()

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def receive: Receive = {
    case SBResolver.HandleSplitBrain(worldView) => handleSplitBrain(worldView).unsafeRunSync()
    case SBResolver.DownAll(worldView)          => downAll(worldView).unsafeRunSync()
  }

  /**
    * Handle the partition using the [[Union]] of the configured
    * strategy and the [[IndirectlyConnected]].
    */
  private def handleSplitBrain(worldView: WorldView): SyncIO[Unit] =
    for {
      _ <- SyncIO(
        log
          .info(s"Received request to handle a split-brain... {}", worldView.simple.asJson.noSpaces)
      )

      // A member that joined during a split-brain might not be aware of all the
      // indirectly-connected nodes and so should not take a decision.
      _ <- isNonJoining.ifM(
        runStrategy(strategy, worldView),
        SyncIO(
          log
            .info("[{}] is joining/weakly-up. Cannot resolve the split-brain.", cluster.selfAddress)
        )
      )
    } yield ()

  /**
    * Handle the partition by downing all the members.
    */
  private def downAll(worldView: WorldView): SyncIO[Unit] =
    for {
      _ <- SyncIO(
        log.info(s"Received request to down all the nodes... {}", worldView.simple.asJson.noSpaces)
      )
      _ <- runStrategy(downAll, worldView)
    } yield ()

  /**
    * Run `strategy` on `worldView`.
    *
    * Enable `nonJoiningOnly` so that joining and weakly-up
    * members do not run the strategy.
    */
  private def runStrategy(
      strategy: Strategy[SyncIO],
      worldView: WorldView
  ): SyncIO[Unit] = {
    def execute(decision: Decision): SyncIO[Unit] =
      for {
        _ <- decision.nodesToDown.toList
          .traverse_(node => SyncIO(cluster.down(node.member.address)))
        _ <- SyncIO(log.info("Downing the nodes: {}", decision.simple.asJson.noSpaces))

      } yield ()

    strategy
      .takeDecision(worldView)
      .flatMap(execute)
      .handleErrorWith(err => SyncIO(log.error(err, "An error occurred during the resolution.")))
  }

  private val isNonJoining: SyncIO[Boolean] = SyncIO(
    cluster.selfMember.status =!= MemberStatus.Joining && cluster.selfMember.status =!= MemberStatus.WeaklyUp
  )
}

object SBResolver {
  def props(
      strategy: Strategy[SyncIO],
      stableAfter: FiniteDuration,
      downAllWhenUnstable: Option[FiniteDuration]
  ): Props =
    Props(new SBResolver(strategy, stableAfter, downAllWhenUnstable))

  private[sbr] sealed trait Event {
    def worldView: WorldView
  }

  private[sbr] final case class HandleSplitBrain(worldView: WorldView) extends Event {
    lazy val simple: HandleSplitBrain.Simple = HandleSplitBrain.Simple(worldView.simple)
  }

  private[sbr] object HandleSplitBrain {
    final case class Simple(worldView: WorldView.Simple)

    object Simple {
      implicit val simpleHandleSplitBrainEncoder: Encoder[Simple] = deriveEncoder
    }
  }

  private[sbr] final case class DownAll(worldView: WorldView) extends Event
}
