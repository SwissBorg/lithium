package com.swissborg.sbr

import cats.tests.StrictCatsEquality
import cats.{Functor, Monoid}
import com.swissborg.sbr.scenarios.Scenario
import com.swissborg.sbr.strategy.Strategy
import com.swissborg.sbr.utils.PostResolution
import org.scalacheck.Arbitrary
import org.scalactic.anyvals._
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

trait SBSpec
    extends WordSpecLike
    with Matchers
    with ScalaCheckPropertyChecks
    with StrictCatsEquality
    with ArbitraryInstances {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = PosInt(1000),
                               maxDiscardedFactor = PosZDouble(5),
                               minSize = PosZInt(0),
                               sizeRange = PosZInt(100),
                               workers = PosInt(4))

  def simulate[F[_]: Functor, Strat[_[_]], S <: Scenario: Arbitrary](name: String)(run: F[Boolean] => Boolean)(
    implicit builder: ArbitraryStrategy[F, Strat],
    ev: Strat[F] <:< Strategy[F],
    M: Monoid[F[PostResolution]]
  ): Unit =
    name in {
      forAll { simulation: Simulation[F, Strat, S] =>
        run(simulation.splitBrainResolved) shouldBe true
      }
    }
}
