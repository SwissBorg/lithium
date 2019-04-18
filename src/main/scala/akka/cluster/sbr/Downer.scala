package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.sbr.strategies.Or
import akka.cluster.sbr.strategies.indirected.Indirected
import akka.cluster.sbr.strategy.Strategy
import akka.cluster.sbr.strategy.ops._

import scala.concurrent.duration._

class Downer[A: Strategy](cluster: Cluster,
                          strategy: A,
                          stableAfter: FiniteDuration,
                          downAllWhenUnstable: FiniteDuration)
    extends Actor
    with ActorLogging {

  import Downer._

  private val _ = context.system.actorOf(StabilityReporter.props(self, stableAfter, downAllWhenUnstable, cluster))

  override def receive: Receive = {
    case h @ HandleSplitBrain(worldView) =>
      log.debug("{}", h)

      Or(strategy, Indirected)
        .takeDecision(worldView)
        .toTry
        .map(execute)
        .get

//    case ClusterIsUnstable(worldView) =>
//      log.debug("Cluster is unstable.")
//      DownAll.takeDecision(worldView).toTry.map(execute).get
  }

  private def execute(decision: StrategyDecision): Unit =
    cluster.state.leader.foreach { leader =>
      if (cluster.selfMember.address == leader) {
        log.debug("Executing decision: {}", decision.clean)
        decision.nodesToDown.foreach(node => cluster.down(node.member.address))
      } else {
        log.debug("Not the leader.")
      }
    }
}

object Downer {
  def props[A: Strategy](cluster: Cluster,
                         strategy: A,
                         stableAfter: FiniteDuration,
                         downAllWhenUnstable: FiniteDuration): Props =
    Props(new Downer(cluster, strategy, stableAfter, downAllWhenUnstable))

  final case class HandleSplitBrain(worldView: WorldView)
//  final case class ClusterIsUnstable(worldView: WorldView)
}
