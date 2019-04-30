package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.sbr._
import akka.cluster.sbr.strategy.{Strategy, StrategyReader}

/**
 * Strategy that will down a partition if it does NOT contain the oldest node.
 *
 * A `role` can be provided (@see [[akka.cluster.sbr.strategies.keepmajority.KeepMajority.Config]]
 * to only take in account the nodes with that role in the decision. This can be useful if there
 * are nodes that are more important than others.
 *
 *
 */
final case class KeepOldest(downIfAlone: Boolean, role: String) extends Strategy {
  override def takeDecision(worldView: WorldView): Either[Throwable, StrategyDecision] =
    KeepOldestView(worldView, downIfAlone, role).map {
      case OldestReachable                 => DownUnreachable(worldView)
      case OldestAlone | OldestUnreachable => DownReachable(worldView)
    }
}

object KeepOldest extends StrategyReader[KeepOldest] {
  override val name: String = "keep-oldest"
}
