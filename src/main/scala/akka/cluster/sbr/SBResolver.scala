package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.sbr.strategies.Or
import akka.cluster.sbr.strategies.indirectlyconnected.IndirectlyConnected
import akka.cluster.sbr.strategy.Strategy
import cats.data.OptionT
import cats.effect.SyncIO
import cats.implicits._

import scala.concurrent.duration._

/**
 * Actor resolving split-brain scenarios.
 *
 * @param strategy the strategy with which to resolved the split-brain.
 * @param stableAfter duration during which a cluster has to be stable before attempting to resolve a split-brain.
 * @param downAllWhenUnstable
 */
class SBResolver(strategy: Strategy, stableAfter: FiniteDuration, downAllWhenUnstable: FiniteDuration)
    extends Actor
    with ActorLogging {
  import SBResolver._

  private val _ = context.system.actorOf(SBReporter.props(self, stableAfter, downAllWhenUnstable))

  private val cluster             = Cluster(context.system)
  private val selfAddress         = cluster.selfMember.address
  private val indirectlyConnected = IndirectlyConnected()

  override def receive: Receive = {
    case h @ HandleSplitBrain(worldView) =>
      log.debug("{}", h)

      Or(strategy, indirectlyConnected)
        .takeDecision(worldView)
        .map(execute)
        .toTry
        .get
        .unsafeRunSync()
  }

  /**
   * Execute the decision by downing all the nodes to be downed if
   * the current node is the leader. Otherwise, do nothing.
   */
  private def execute(decision: StrategyDecision): SyncIO[Unit] = {
    val leader: OptionT[SyncIO, Address] = OptionT(SyncIO(cluster.state.leader))

    def down(nodes: Set[Node]): OptionT[SyncIO, Unit] =
      OptionT.liftF(nodes.toList.traverse_(node => SyncIO(cluster.down(node.member.address))))

    leader
      .map(_ == selfAddress)
      .ifM(down(decision.nodesToDown) >> OptionT.liftF(SyncIO(log.debug("Executing decision: {}", decision.clean))),
           OptionT.pure(()))
      .value
      .void
  }
}

object SBResolver {
  def props(strategy: Strategy, stableAfter: FiniteDuration, downAllWhenUnstable: FiniteDuration): Props =
    Props(new SBResolver(strategy, stableAfter, downAllWhenUnstable))

  final case class HandleSplitBrain(worldView: WorldView)
}
