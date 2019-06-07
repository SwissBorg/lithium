package com.swissborg.sbr

import cats.implicits._
import cats.tests.StrictCatsEquality
import cats.{Functor, Monoid}
import com.swissborg.sbr.scenarios.Scenario
import com.swissborg.sbr.strategy.Strategy
import com.swissborg.sbr.utils.PostResolution
import org.scalacheck.Arbitrary
import org.scalactic.anyvals._
import org.scalatest.{Assertion, Matchers, WordSpecLike}
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

  /**
    * Checks that the strategy can handle the given scenario.
    *
    * @param name the name of the test.
    * @param run run the effect to get assertion result.
    * @tparam F the effect in which the simulation is run.
    * @tparam Strat the strategy to use.
    * @tparam S the scenario.
    */
  def simulate[F[_]: Functor, Strat[_[_]], S <: Scenario: Arbitrary](name: String)(
      run: F[Assertion] => Assertion)(
      implicit strategy: ArbitraryStrategy[Strat[F]],
      ev: Strat[F] <:< Strategy[F],
      M: Monoid[F[PostResolution]]
  ): Unit =
    name in {
      forAll { simulation: Simulation[F, Strat, S] =>
        run(simulation.splitBrainResolved.map(_ shouldBe true))
      }
    }
}
