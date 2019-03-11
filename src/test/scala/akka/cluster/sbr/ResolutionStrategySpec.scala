package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.Scenario.SymmetricSplitScenario
import cats.Monoid
import cats.implicits._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._
import org.scalacheck.Prop
import org.scalacheck.Prop._

class ResolutionStrategySpec extends MySpec {
  import ResolutionStrategySpec._

  "ResolutionStrategy" - {
    "staticQuorum" - {
      "1 - should follow the specs" in {
        forAll {
          (maybeReachableNodes: Either[NoReachableNodesError.type, ReachableNodes],
           unreachableNodes: UnreachableNodes) =>
            whenever(maybeReachableNodes.isRight) {
              val reachableNodes = maybeReachableNodes.right.get

              ResolutionStrategy.staticQuorum(reachableNodes, unreachableNodes) match {
                case DownReachable(nodeGroup) =>
                  reachableNodes match {
                    case ReachableQuorum(reachableNodes)    => fail
                    case ReachableSubQuorum(reachableNodes) => succeed
                  }

                case DownUnreachable(nodeGroup) =>
                  reachableNodes match {
                    case ReachableQuorum(reachableNodes)    => succeed
                    case ReachableSubQuorum(reachableNodes) => fail
                  }

                case UnsafeDownReachable(nodeGroup) =>
                  (reachableNodes, unreachableNodes) match {
                    case (_: ReachableQuorum, _: UnreachablePotentialQuorum) => succeed
                    case _                                                   => fail
                  }

                case Idle() =>
                  unreachableNodes match {
                    case EmptyUnreachable() => succeed
                    case _                  => fail("Should not idle if there are unreachable nodes.")
                  }
              }
            }

        }
      }

      "2 - should correctly handle a symmetric split scenarios with a correctly defined quorum size" in {
        forAll { (scenario: SymmetricSplitScenario, quorumSize: QuorumSize) =>
          whenever(quorumSize > (scenario.clusterSize / 2)) {
            val remainingSubClusters: RemainingSubClusters = scenario.worldViews.foldMap { worldView =>
              ResolutionStrategy
                .staticQuorum(
                  ReachableNodes(worldView, quorumSize).right.get, // SymmetricSplitScenario has at least one reachable node
                  UnreachableNodes(worldView, quorumSize)
                ) match {
                case DownReachable(_)       => RemainingSubClusters(0)
                case UnsafeDownReachable(_) => RemainingSubClusters(0)
                case DownUnreachable(_)     => RemainingSubClusters(1)
                case Idle()                 => RemainingSubClusters(1)
              }
            }

//            println(remainingSubClusters.n)
            remainingSubClusters.n.value should be <= 1
          }
        }
      }
    }
  }
}

object ResolutionStrategySpec {
  final case class RemainingSubClusters(n: Int Refined NonNegative)

  object RemainingSubClusters {
    implicit val remainingSubClustersMonoid: Monoid[RemainingSubClusters] = new Monoid[RemainingSubClusters] {
      override def empty: RemainingSubClusters = RemainingSubClusters(refineMV[NonNegative](0))

      override def combine(x: RemainingSubClusters, y: RemainingSubClusters): RemainingSubClusters =
        RemainingSubClusters(refineV[NonNegative](x.n.value + y.n.value).right.get)
    }
  }

  def classifyNetwork(reachableNodes: ReachableNodes, unreachableNodes: UnreachableNodes)(prop: Prop): Prop = {
    val isNormal: Boolean =
      (reachableNodes, unreachableNodes) match {
        case (_, _: EmptyUnreachable) => true
        case _                        => false
      }

    val reachableIsQuorum: Boolean = reachableNodes match {
      case _: ReachableQuorum    => true
      case _: ReachableSubQuorum => false
    }

    val unreachableIsPotentialQuorum: Boolean = unreachableNodes match {
      case _: UnreachablePotentialQuorum => true
      case _                             => false
    }

    classify(isNormal, "normal", "split-brain") {
      classify(reachableIsQuorum, "reachable-is-quorum", "reachable-is-not-quorum") {
        classify(unreachableIsPotentialQuorum, "unreachable-is-maybe-quorum", "unreachable-is-not-quorum") {
          prop
        }
      }
    }
  }
}
