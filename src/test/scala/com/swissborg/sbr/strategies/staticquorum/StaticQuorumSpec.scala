package com.swissborg.sbr.strategies.staticquorum

import cats.effect.{Sync, SyncIO}
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios.{OldestRemovedScenario, SymmetricSplitScenario}
import com.swissborg.sbr.strategies.staticquorum.StaticQuorum.Config
import com.swissborg.sbr.utils.PostResolution
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._

class StaticQuorumSpec extends SBSpec {

  "StaticQuorum" must {
    "handle symmetric split scenarios with a correctly defined quorum size" in {
      forAll { scenario: SymmetricSplitScenario =>
        implicit val _: Arbitrary[StaticQuorum[SyncIO]] = StaticQuorumSpec.arbStaticQuorum(scenario.clusterSize)

        forAll { staticQuorum: StaticQuorum[SyncIO] =>
          val remainingPartitions: PostResolution = scenario.worldViews
            .foldMap { worldView =>
              staticQuorum.takeDecision(worldView).map(PostResolution.fromDecision(worldView))
            }
            .unsafeRunSync()

          remainingPartitions.noSplitBrain shouldBe true
        }
      }
    }

    "handle a split during the oldest-removed scenarios" in {
      forAll { scenario: OldestRemovedScenario =>
        implicit val _: Arbitrary[StaticQuorum[SyncIO]] = StaticQuorumSpec.arbStaticQuorum(scenario.clusterSize)

        forAll { staticQuorum: StaticQuorum[SyncIO] =>
          val remainingSubClusters = scenario.worldViews
            .foldMap { worldView =>
              staticQuorum.takeDecision(worldView).map(PostResolution.fromDecision(worldView))
            }
            .unsafeRunSync()

          remainingSubClusters.noSplitBrain shouldBe true
        }
      }
    }
    // TODO check if can really be handled
//    "2 - should handle split during up-dissemination" in {
//      forAll { scenario: UpDisseminationScenario =>
//        implicit val _: Arbitrary[Config] = com.swissborg.sbr.strategies.staticquorum.StaticQuorumSpec.arbConfig(scenario.clusterSize)
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
  private def arbStaticQuorum[F[_]: Sync](clusterSize: Int Refined Positive): Arbitrary[StaticQuorum[F]] = Arbitrary {
    val minQuorumSize = clusterSize / 2 + 1
    for {
      quorumSize <- chooseNum(minQuorumSize, clusterSize.value)
      role       <- arbitrary[String]
    } yield StaticQuorum(Config(role, refineV[Positive](quorumSize).right.get))
  }
}
