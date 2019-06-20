package com.swissborg.sbr.resolver

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.MemberStatus.{Joining, WeaklyUp}
import akka.cluster.{Cluster, Member, MemberStatus}
import cats.effect.SyncIO
import cats.implicits._
import com.swissborg.sbr.WorldView.SimpleWorldView
import com.swissborg.sbr._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.resolver.SBResolver.HandleSplitBrain.Simple
import com.swissborg.sbr.splitbrain.SBSplitBrainReporter
import com.swissborg.sbr.strategy.indirectlyconnected.IndirectlyConnected
import com.swissborg.sbr.strategy.{Strategy, StrategyDecision, Union, downall}
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
private[sbr] class SBResolver(
    private val _strategy: Strategy[SyncIO],
    private val stableAfter: FiniteDuration,
    private val downAllWhenUnstable: Option[FiniteDuration]
) extends Actor
    with ActorLogging {

  import SBResolver._

  context.actorOf(
    SBSplitBrainReporter.props(self, stableAfter, downAllWhenUnstable),
    "splitbrain-reporter"
  )

  private val cluster: Cluster = Cluster(context.system)

  private val selfAddress: Address = cluster.selfMember.address

  private val strategy: Union[SyncIO, Strategy, IndirectlyConnected] =
    new Union(_strategy, new IndirectlyConnected)

  private val downAll: downall.DownAll[SyncIO] = new downall.DownAll()

  override def receive: Receive = {
    case HandleSplitBrain(worldView) =>
      log.info(s"Received request to handle a split-brain... {}", worldView.simple.asJson.noSpaces)
      runStrategy(strategy, worldView).unsafeRunSync()

    case DownAll(worldView) =>
      log.info(s"Received request to down all the nodes... {}", worldView.simple.asJson.noSpaces)
      runStrategy(downAll, worldView).unsafeRunSync()
  }

  private def runStrategy(strategy: Strategy[SyncIO], worldView: WorldView): SyncIO[Unit] = {
    // For downing unreachable nodes
    def down(member: Member): SyncIO[Unit] = SyncIO(cluster.down(member.address))

    // Gracefully leave the cluster
    def leave(member: Member): SyncIO[Unit] = SyncIO(cluster.leave(member.address))

    // Reachable nodes:
    //   A change to the `leaving` state is issued to allow
    //   for them to be able to gracefully shutdown.
    //
    // Unreachable nodes:
    //   A change to the `down` state is issued. They will
    //   be remove from the cluster on convergence, and the
    //   nodes can start leaving and joining the cluster again.
    //
    // Indirectly-connect nodes:
    //   If it's the current node it request a change to the `leaving`
    //   state. This will allow it to gracefully leave the cluster.
    //   The other nodes will simply be downed as they are actually
    //   seen as unreachable by akka-cluster. Nodes ignore gossips
    //   from unreachable nodes (so also by extension the indirectly-
    //   connected ones). Therefore, an indirectly-connected node
    //   will ignore down requests and always choose to leave gracefully.
    def actOnAll(decision: StrategyDecision): SyncIO[Unit] =
      decision.nodesToDown.toList.traverse_ {
        case ReachableNode(member) =>
          leave(member)

        case UnreachableNode(member) =>
          down(member)

        case IndirectlyConnectedNode(member) =>
          if (member.address === selfAddress) {
            leave(member)
          } else {
            down(member)
          }
      } >> SyncIO(log.info("Downing the nodes: {}", decision.simple.asJson.noSpaces))

    // The current nodes is joining or weakly-up and could have
    // a wrong state as it might have missed contentions.
    // It will only leave if the decision says so and ignore all
    // the other nodes to down.
    def actOnSelfOnly(decision: StrategyDecision, status: MemberStatus): SyncIO[Unit] =
      decision.nodesToDown
        .find(_.member.address === selfAddress)
        .fold(
          SyncIO(log.info("The node is in the '{}' state. Cannot act on the decision.", status))
        ) { node =>
          leave(node.member) >>
            SyncIO(
              log.info(
                "The node is in the '{}' state. Can only leave the cluster. Cannot fully act on the decision.",
                status
              )
            )
        }

    def execute(decision: StrategyDecision): SyncIO[Unit] = {
      val selfStatus = cluster.selfMember.status

      if (selfStatus === Joining || selfStatus === WeaklyUp) actOnSelfOnly(decision, selfStatus)
      else actOnAll(decision)
    }

    strategy.takeDecision(worldView).flatMap(execute)
  }.handleErrorWith(err => SyncIO(log.error(err, "An error occurred during decision making.")))
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
    lazy val simple: HandleSplitBrain.Simple = Simple(worldView.simple)
  }

  private[sbr] object HandleSplitBrain {
    final case class Simple(worldView: SimpleWorldView)

    object Simple {
      implicit val simpleHandleSplitBrainEncoder: Encoder[Simple] = deriveEncoder
    }
  }

  private[sbr] final case class DownAll(worldView: WorldView) extends Event
}
