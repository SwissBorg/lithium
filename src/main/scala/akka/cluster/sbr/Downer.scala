package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.sbr.implicits._
import akka.cluster.sbr.strategies.Or
import akka.cluster.sbr.strategies.downall.DownAll
import akka.cluster.sbr.strategies.indirected.Indirected
import akka.cluster.sbr.strategy.Strategy
import akka.cluster.sbr.strategy.ops._
import cats.implicits._

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
    case ClusterIsStable(worldView) =>
      log.debug("Cluster is stable, resolving...")
      Or(strategy, Indirected).takeDecision(worldView).toTry.map(executeDecision).get

    case ClusterIsUnstable(worldView) =>
      log.debug("Cluster is unstable, downing all...")
      DownAll.takeDecision(worldView).toTry.map(executeDecision).get
  }

  /**
   * Executes the decision.
   *
   * If the current node is the leader all the nodes referred in the decision
   * will be downed. Otherwise, if it is not the leader or none exists, and refers to itself.
   * It will down the current node. Else, no node will be downed.
   *
   * In short, the leader can down anyone. Other nodes are only allowed to down themselves.
   */
  private def executeDecision(decision: StrategyDecision): Unit =
    if (cluster.state.leader.contains(cluster.selfAddress)) {
      val nodesToDown = decision.nodesToDown
      log.debug(s"[execute-decision] Downing nodes: $nodesToDown")
      nodesToDown.foreach(node => cluster.down(node.member.address))
    } else {
      if (decision.nodesToDown.map(_.member).contains(cluster.selfMember)) {
        log.debug(s"[execute-decision] Downing self")
        cluster.down(cluster.selfAddress)
      } else {
        log.debug(s"[execute-decision] Not downing anything.")
      }
    }
}

object Downer {
  def props[A: Strategy](cluster: Cluster,
                         strategy: A,
                         stableAfter: FiniteDuration,
                         downAllWhenUnstable: FiniteDuration): Props =
    Props(new Downer(cluster, strategy, stableAfter, downAllWhenUnstable))

  final case class ClusterIsStable(worldView: WorldView)
  final case class ClusterIsUnstable(worldView: WorldView)
}
