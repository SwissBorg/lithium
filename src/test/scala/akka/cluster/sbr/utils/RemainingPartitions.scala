package akka.cluster.sbr.utils

import akka.cluster.sbr._
import cats.Monoid
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import cats.implicits._

/**
 * Represents how many partitions are left after running
 * a split-brain resolution strategy. If `n` > 1 then a
 * split-brain has appeared.
 */
final case class RemainingPartitions(n: Int Refined NonNegative)

object RemainingPartitions {
  def fromDecision(worldView: WorldView)(decision: StrategyDecision): RemainingPartitions = decision match {
    case DownThese(decision1, decision2) => fromDecision(worldView)(decision1) |+| fromDecision(worldView)(decision2)
    case _: DownUnreachable              => RemainingPartitions(1)
    case _: DownReachable                => RemainingPartitions(0)
    case _: DownSelf                     => RemainingPartitions(0)
    case _: Idle.type =>
      if (worldView.reachableConsideredNodes.isEmpty) RemainingPartitions(0) // node is effectively down
      else RemainingPartitions(1)
  }

  implicit val remainingSubClustersMonoid: Monoid[RemainingPartitions] = new Monoid[RemainingPartitions] {
    override def empty: RemainingPartitions = RemainingPartitions(0)

    override def combine(x: RemainingPartitions, y: RemainingPartitions): RemainingPartitions =
      RemainingPartitions(refineV[NonNegative](x.n.value + y.n.value).right.get)
  }
}
