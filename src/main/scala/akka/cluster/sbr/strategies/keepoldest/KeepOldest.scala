package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.sbr._
import akka.cluster.sbr.strategies.keepoldest.KeepOldestView.KeepOldestViewError
import akka.cluster.sbr.strategy.Strategy

/**
 * Strategy that will down a partition if it does NOT contain the oldest node.
 *
 * A `role` can be provided (@see [[akka.cluster.sbr.strategies.keepmajority.KeepMajority.Config]]
 * to only take in account the nodes with that role in the decision. This can be useful if there
 * are nodes that are more important than others.
 *
 *
 */
final case class KeepOldest(downIfAlone: Boolean, role: String)

object KeepOldest {
  def keepOldest(strategy: KeepOldest, worldView: WorldView): Either[KeepOldestViewError, StrategyDecision] =
    KeepOldestView(worldView, strategy.downIfAlone, strategy.role).map {
      case OldestReachable                 => DownUnreachable(worldView)
      case OldestAlone | OldestUnreachable => DownReachable(worldView)
    }

  implicit val keepOldestStrategy: Strategy[KeepOldest] = new Strategy[KeepOldest] {
    override def handle(strategy: KeepOldest, worldView: WorldView): Either[Throwable, StrategyDecision] =
      keepOldest(strategy, worldView)
  }

  implicit val keepOldestStrategyReader: StrategyReader[KeepOldest] = StrategyReader.fromName("keep-oldest")
}
