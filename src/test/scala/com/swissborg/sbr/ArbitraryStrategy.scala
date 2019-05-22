package com.swissborg.sbr

import cats.{Applicative, ApplicativeError}
import cats.effect.Sync
import com.swissborg.sbr.scenarios.Scenario
import com.swissborg.sbr.strategies.keepmajority.KeepMajority
import com.swissborg.sbr.strategies.keepoldest.KeepOldest
import com.swissborg.sbr.strategies.keepreferee.KeepReferee
import com.swissborg.sbr.strategies.keepreferee.KeepReferee.Config.Address
import com.swissborg.sbr.strategies.staticquorum.StaticQuorum
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.chooseNum

trait ArbitraryStrategy[F[_], Strategy[_[_]]] {
  def fromScenario(scenario: Scenario): Arbitrary[Strategy[F]]
}

object ArbitraryStrategy {
  implicit def keepRefereeStrategyBuilder[F[_]: Applicative]: ArbitraryStrategy[F, KeepReferee] =
    new ArbitraryStrategy[F, KeepReferee] {
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

  implicit def staticQuorumStrategyBuilder[F[_]: Sync]: ArbitraryStrategy[F, StaticQuorum] =
    new ArbitraryStrategy[F, StaticQuorum] {
      override def fromScenario(scenario: Scenario): Arbitrary[StaticQuorum[F]] = Arbitrary {
        val clusterSize = scenario.clusterSize

        val minQuorumSize = clusterSize / 2 + 1
        for {
          quorumSize <- chooseNum(minQuorumSize, clusterSize.value)
          role       <- arbitrary[String]
        } yield new StaticQuorum(StaticQuorum.Config(role, refineV[Positive](quorumSize).right.get))
      }
    }

  implicit def keepMajorityStrategyBuilder[F[_]](
    implicit ev: ApplicativeError[F, Throwable]
  ): ArbitraryStrategy[F, KeepMajority] = new ArbitraryStrategy[F, KeepMajority] {
    override def fromScenario(scenario: Scenario): Arbitrary[KeepMajority[F]] =
      Arbitrary(arbitrary[String].map(role => new KeepMajority(KeepMajority.Config(role))))
  }

  implicit def keepOldestStrategyBuilder[F[_]](
    implicit F: ApplicativeError[F, Throwable]
  ): ArbitraryStrategy[F, KeepOldest] = new ArbitraryStrategy[F, KeepOldest] {
    override def fromScenario(scenario: Scenario): Arbitrary[KeepOldest[F]] = Arbitrary {
      for {
        downIfAlone <- arbitrary[Boolean]
        role        <- arbitrary[String]
      } yield new KeepOldest(KeepOldest.Config(downIfAlone, role))
    }

  }
}
