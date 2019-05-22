package com.swissborg.sbr

import cats.{Applicative, ApplicativeError}
import cats.effect.Sync
import com.swissborg.sbr.scenarios.Scenario
import com.swissborg.sbr.strategies.downall.DownAll
import com.swissborg.sbr.strategies.keepmajority.KeepMajority
import com.swissborg.sbr.strategies.keepoldest.KeepOldest
import com.swissborg.sbr.strategies.keepreferee.KeepReferee
import com.swissborg.sbr.strategies.keepreferee.KeepReferee.Config.Address
import com.swissborg.sbr.strategies.staticquorum.StaticQuorum
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.chooseNum

trait ArbitraryStrategy[F] {
  def fromScenario(scenario: Scenario): Arbitrary[F]
}

object ArbitraryStrategy {
  implicit def keepRefereeStrategyBuilder[F[_]: Applicative]: ArbitraryStrategy[KeepReferee[F]] =
    new ArbitraryStrategy[KeepReferee[F]] {
      override def fromScenario(scenario: Scenario): Arbitrary[KeepReferee[F]] = Arbitrary {
        val nodes = scenario.worldViews.head.nodes

        for {
          pick <- chooseNum(0, nodes.length - 1)
          referee = nodes.toNonEmptyList.toList.apply(pick)
          downIfLessThan <- chooseNum(1, nodes.length)
        } yield
          new KeepReferee[F](
            KeepReferee.Config(refineV[Address](referee.member.address.toString).right.get,
                               refineV[Positive](downIfLessThan).right.get)
          )
      }
    }

  implicit def staticQuorumStrategyBuilder[F[_]: Sync]: ArbitraryStrategy[StaticQuorum[F]] =
    new ArbitraryStrategy[StaticQuorum[F]] {
      override def fromScenario(scenario: Scenario): Arbitrary[StaticQuorum[F]] = Arbitrary {
        val clusterSize = scenario.clusterSize

        val minQuorumSize = clusterSize / 2 + 1
        for {
          quorumSize <- chooseNum(minQuorumSize, clusterSize.value)
          role       <- arbitrary[String]
        } yield new StaticQuorum(StaticQuorum.Config(role, refineV[Positive](quorumSize).right.get))
      }
    }

  implicit def keepMajorityStrategyBuilder[F[_]: ApplicativeError[?[_], Throwable]]: ArbitraryStrategy[KeepMajority[F]] =
    new ArbitraryStrategy[KeepMajority[F]] {
      override def fromScenario(scenario: Scenario): Arbitrary[KeepMajority[F]] =
        Arbitrary(arbitrary[String].map(role => new KeepMajority(KeepMajority.Config(role))))
    }

  implicit def keepOldestStrategyBuilder[F[_]: ApplicativeError[?[_], Throwable]]: ArbitraryStrategy[KeepOldest[F]] =
    new ArbitraryStrategy[KeepOldest[F]] {
      override def fromScenario(scenario: Scenario): Arbitrary[KeepOldest[F]] = Arbitrary {
        for {
          downIfAlone <- arbitrary[Boolean]
          role        <- arbitrary[String]
        } yield new KeepOldest(KeepOldest.Config(downIfAlone, role))
      }

    }

  implicit def downAllStrategyBuilder[F[_]: Applicative]: ArbitraryStrategy[DownAll[F]] =
    new ArbitraryStrategy[DownAll[F]] {
      override def fromScenario(scenario: Scenario): Arbitrary[DownAll[F]] = Arbitrary(Gen.const(new DownAll[F]()))
    }
}
