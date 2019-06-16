package com.swissborg.sbr.resolver

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.MemberStatus.{Joining, WeaklyUp}
import cats.data.OptionT
import cats.data.OptionT._
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr.WorldView.SimpleWorldView
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.resolver.SBResolver.HandleSplitBrain.SimpleHandleSplitBrain
import com.swissborg.sbr.splitbrain.SBSplitBrainReporter
import com.swissborg.sbr.strategy.indirectlyconnected.IndirectlyConnected
import com.swissborg.sbr.strategy.{Strategy, StrategyDecision, Union}
import com.swissborg.sbr.{Node, WorldView}
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._

import scala.concurrent.duration._

/**
  * Actor resolving split-brain scenarios.
  *
  * @param _strategy the strategy with which to resolved the split-brain.
  * @param stableAfter duration during which a cluster has to be stable before attempting to resolve a split-brain.
  * @param downAllWhenUnstable down the partition if the cluster has been unstable for longer than `stableAfter + 3/4 * stableAfter`.
  */
class SBResolver(
    _strategy: Strategy[SyncIO],
    stableAfter: FiniteDuration,
    downAllWhenUnstable: Boolean
) extends Actor
    with ActorLogging {

  import SBResolver._

  context.actorOf(SBSplitBrainReporter.props(self, stableAfter, downAllWhenUnstable))

  private val cluster: Cluster = Cluster(context.system)
  private val selfAddress: Address = cluster.selfMember.address
  private val defaultStrategy: Union[SyncIO] = new Union(_strategy, new IndirectlyConnected)
  private val downAll: com.swissborg.sbr.strategy.downall.DownAll[SyncIO] =
    new com.swissborg.sbr.strategy.downall.DownAll()

  override def receive: Receive = {
    case e @ HandleSplitBrain(worldView) =>
      log.info("Received split-brain to resolve...")
      log.info(e.simple.asJson.noSpaces)

      runStrategy(defaultStrategy, worldView).unsafeRunSync()

    case DownAll(worldView) =>
      log.info("Downing all...")
      log.info(worldView.simple.asJson.noSpaces)

      runStrategy(downAll, worldView).unsafeRunSync()
  }

  private def runStrategy(strategy: Strategy[SyncIO], worldView: WorldView): SyncIO[Unit] = {
    def down(nodes: Set[Node]): OptionT[SyncIO, Unit] =
      liftF(nodes.toList.traverse_(node => SyncIO(cluster.down(node.member.address))))

    // Execute the decision by downing all the nodes to be downed if
    // the current node is the leader. Otherwise, do nothing.
    def execute(decision: StrategyDecision): SyncIO[Unit] = {
      val leader: OptionT[SyncIO, Address] = OptionT(SyncIO(cluster.state.leader))

      leader
        .flatMap(
          leader =>
            if (leader === selfAddress && (cluster.selfMember.status === Joining || cluster.selfMember.status === WeaklyUp)) {
              // A leader that joined during a split-brain might not be aware of all the
              // indirectly-connected nodes and so should not take a decision.
              liftF(
                SyncIO(
                  log.warning(
                    "SPLIT-BRAIN MUST BE MANUALLY RESOLVED. The leader is joining the cluster and misses information."
                  )
                )
              ).as(true)
            } else {
              pure(leader === selfAddress)
            }
        )
        .ifM(
          down(decision.nodesToDown) >> liftF(SyncIO(log.info(decision.simple.asJson.noSpaces))),
          liftF(SyncIO(log.info("Cannot take a decision. Not the leader.")))
        )
        .value
        .void
    }

    strategy.takeDecision(worldView).flatMap(execute)
  }.handleErrorWith(err => SyncIO(log.error(err, "An error occurred during decision making.")))
}

object SBResolver {
  def props(
      strategy: Strategy[SyncIO],
      stableAfter: FiniteDuration,
      downAllWhenUnstable: Boolean
  ): Props =
    Props(new SBResolver(strategy, stableAfter, downAllWhenUnstable))

  sealed trait Event {
    def worldView: WorldView
  }

  final case class HandleSplitBrain(worldView: WorldView) extends Event {
    lazy val simple: SimpleHandleSplitBrain = SimpleHandleSplitBrain(worldView.simple)
  }

  object HandleSplitBrain {
    final case class SimpleHandleSplitBrain(worldView: SimpleWorldView)

    object SimpleHandleSplitBrain {
      implicit val simpleHandleSplitBrainEncoder: Encoder[SimpleHandleSplitBrain] = deriveEncoder
    }
  }

  final case class DownAll(worldView: WorldView) extends Event
}
