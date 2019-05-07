package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.sbr.strategies.Union
import akka.cluster.sbr.strategies.indirectlyconnected.IndirectlyConnected
import akka.cluster.sbr.strategy.Strategy
import cats.data.OptionT
import cats.effect.SyncIO
import cats.implicits._

import scala.concurrent.duration._

/**
 * Actor resolving split-brain scenarios.
 *
 * @param _strategy the strategy with which to resolved the split-brain.
 * @param stableAfter duration during which a cluster has to be stable before attempting to resolve a split-brain.
 */
class SBResolver(_strategy: Strategy, stableAfter: FiniteDuration) extends Actor with ActorLogging {

  import SBResolver._

  private val _ = context.system.actorOf(SBReporter.props(self, stableAfter))

  private val cluster     = Cluster(context.system)
  private val selfAddress = cluster.selfMember.address
  private val strategy    = Union(_strategy, IndirectlyConnected())

  override def receive: Receive = {
    case h @ HandleSplitBrain(worldView) =>
      log.debug("{}", h)

      runStrategy(strategy, worldView)
        .leftMap(err => SyncIO(log.error(err, "An error occurred during decision making.")))
        .fold(_.unsafeRunSync(), _.unsafeRunSync())
  }

  private def runStrategy(strategy: Strategy, worldView: WorldView): Either[Throwable, SyncIO[Unit]] = {
    def down(nodes: Set[Node]): OptionT[SyncIO, Unit] =
      OptionT.liftF(nodes.toList.traverse_(node => SyncIO(cluster.down(node.member.address))))

    // Execute the decision by downing all the nodes to be downed if
    // the current node is the leader. Otherwise, do nothing.
    def execute(decision: StrategyDecision): SyncIO[Unit] = {
      val leader: OptionT[SyncIO, Address] = OptionT(SyncIO(cluster.state.leader))

      leader
        .map(_ == selfAddress)
        .ifM(down(decision.nodesToDown) >> OptionT.liftF(SyncIO(log.debug("Executing decision: {}", decision.clean))),
             OptionT.pure(()))
        .value
        .void
    }

    strategy.takeDecision(worldView).map(execute)
  }
}

object SBResolver {
  def props(strategy: Strategy, stableAfter: FiniteDuration): Props = Props(new SBResolver(strategy, stableAfter))

  final case class HandleSplitBrain(worldView: WorldView)
}
