package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.Scenario.SymmetricSplitScenario
import akka.cluster.sbr._
import akka.cluster.sbr.strategies.staticquorum.ArbitraryInstances._
import akka.cluster.sbr.utils.RemainingSubClusters
import cats.Monoid
import cats.implicits._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import org.scalacheck.Prop
import org.scalacheck.Prop.classify

class StaticQuorumSpec extends MySpec {
  import StaticQuorumSpec._

  "StaticQuorum" - {
    // TODO
    "1 - should follow the specs" in {
//      forAll { (worldView: WorldView, quorumSize: QuorumSize) =>
//        Strategy[StaticQuorum](worldView, quorumSize) match {
//          case DownReachable(nodeGroup) =>
//            reachableNodes match {
//              case ReachableQuorum(reachableNodes)    => fail
//              case ReachableSubQuorum(reachableNodes) => succeed
//            }
//
//          case DownUnreachable(nodeGroup) =>
//            reachableNodes match {
//              case ReachableQuorum(reachableNodes)    => succeed
//              case ReachableSubQuorum(reachableNodes) => fail
//            }
//
//          case UnsafeDownReachable(nodeGroup) =>
//            (reachableNodes, unreachableNodes) match {
//              case (_: ReachableQuorum, _: StaticQuorumUnreachablePotentialQuorum) => succeed
//              case _                                                               => fail
//            }
//
//          case Idle =>
//            unreachableNodes match {
//              case EmptyStaticQuorumUnreachable() => succeed
//              case _                              => fail("Should not idle if there are unreachable nodes.")
//            }
//
//        }
//      }
    }

    "2 - should handle symmetric split scenarios with a correctly defined quorum size" in {
      forAll { (scenario: SymmetricSplitScenario, quorumSize: QuorumSize) =>
        whenever(quorumSize > (scenario.clusterSize / 2)) {
          val remainingSubClusters: RemainingSubClusters = scenario.worldViews.foldMap { worldView =>
            Strategy[StaticQuorum](worldView, quorumSize).foldMap(RemainingSubClusters.fromDecision)
          }

          remainingSubClusters.n.value should be <= 1
        }
      }
    }
  }

}

object StaticQuorumSpec {
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
      case _: StaticQuorumUnreachablePotentialQuorum => true
      case _                                         => false
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
