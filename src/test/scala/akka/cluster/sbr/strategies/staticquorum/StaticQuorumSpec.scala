package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.strategy.ops._
import akka.cluster.sbr._
import akka.cluster.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario}
import akka.cluster.sbr.utils.RemainingPartitions
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck.Prop.classify
import org.scalacheck.{Arbitrary, Prop}

class StaticQuorumSpec extends MySpec {

  "StaticQuorum" - {
    "1 - should handle symmetric split scenarios with a correctly defined quorum size" in {
      forAll { scenario: SymmetricSplitScenario =>
        implicit val _: Arbitrary[StaticQuorum] = StaticQuorumSpec.arbStaticQuorum(scenario.clusterSize)

        forAll { staticQuorum: StaticQuorum =>
          val remainingSubClusters: RemainingPartitions = scenario.worldViews.foldMap { worldView =>
            staticQuorum.takeDecision(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
          }

          remainingSubClusters.n.value should be <= 1
        }
      }
    }

    "2 - should handle a split during the oldest-removed scenarios" in {
      forAll { scenario: OldestRemovedScenario =>
        implicit val _: Arbitrary[StaticQuorum] = StaticQuorumSpec.arbStaticQuorum(scenario.clusterSize)

        forAll { staticQuorum: StaticQuorum =>
          val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
            staticQuorum.takeDecision(worldView).foldMap(RemainingPartitions.fromDecision(worldView))
          }

          remainingSubClusters.n.value should be <= 1
        }
      }
    }
    // TODO check if can really be handled
//    "2 - should handle split during up-dissemination" in {
//      forAll { scenario: UpDisseminationScenario =>
//        implicit val _: Arbitrary[Config] = akka.cluster.sbr.strategies.staticquorum.StaticQuorumSpec.arbConfig(scenario.clusterSize)
//
//        forAll { config: Config =>
//          val remainingSubClusters = scenario.worldViews.foldMap { worldView =>
//            Strategy[StaticQuorum](worldView, config).foldMap(RemainingPartitions.fromDecision)
//          }
//
//          remainingSubClusters.n.value should be <= 1
//        }
//      }
//    }
  }
}

object StaticQuorumSpec {
  private def arbStaticQuorum(clusterSize: Int Refined Positive): Arbitrary[StaticQuorum] = Arbitrary {
    val minQuorumSize = clusterSize / 2 + 1
    for {
      quorumSize <- chooseNum(minQuorumSize, clusterSize.value)
      role       <- arbitrary[String]
    } yield StaticQuorum(role, refineV[Positive](quorumSize).right.get)
  }

  def classifyNetwork(reachableNodes: ReachableNodes, unreachableNodes: UnreachableNodes)(prop: Prop): Prop = {
    val isNormal: Boolean =
      (reachableNodes, unreachableNodes) match {
        case (_, EmptyUnreachable) => true
        case _                     => false
      }

    val reachableIsQuorum: Boolean = reachableNodes match {
      case ReachableQuorum    => true
      case ReachableSubQuorum => false
    }

    val unreachableIsPotentialQuorum: Boolean = unreachableNodes match {
      case UnreachablePotentialQuorum => true
      case _                          => false
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
