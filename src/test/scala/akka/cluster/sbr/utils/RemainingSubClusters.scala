package akka.cluster.sbr.utils

import akka.cluster.sbr._
import cats.Monoid
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.{refineMV, refineV}

final case class RemainingSubClusters(n: Int Refined NonNegative)

object RemainingSubClusters {
  def fromDecision(decision: StrategyDecision): RemainingSubClusters = decision match {
    case DownReachable(nodeGroup)       => RemainingSubClusters(0)
    case UnsafeDownReachable(nodeGroup) => RemainingSubClusters(0)
    case DownUnreachable(nodeGroup)     => RemainingSubClusters(1)
    case Idle                           => RemainingSubClusters(1)
  }

  implicit val remainingSubClustersMonoid: Monoid[RemainingSubClusters] = new Monoid[RemainingSubClusters] {
    override def empty: RemainingSubClusters = RemainingSubClusters(0)

    override def combine(x: RemainingSubClusters, y: RemainingSubClusters): RemainingSubClusters =
      RemainingSubClusters(refineV[NonNegative](x.n.value + y.n.value).right.get)
  }
}
