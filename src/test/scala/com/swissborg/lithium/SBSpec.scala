package com.swissborg.lithium

import cats.implicits._
import cats.tests.StrictCatsEquality
import cats.{Applicative, Functor, Monoid}
import com.swissborg.lithium.ArbitraryStrategy._
import com.swissborg.lithium.instances.ArbitraryTestInstances
import com.swissborg.lithium.strategy._
import com.swissborg.lithium.utils.PostResolution
import org.scalacheck.Arbitrary
import org.scalactic.anyvals._
import org.scalatest.{Assertion, Matchers, WordSpecLike}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

trait SBSpec
    extends WordSpecLike
    with Matchers
    with ScalaCheckPropertyChecks
    with StrictCatsEquality
    with ArbitraryTestInstances {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(
      minSuccessful = PosInt(1000),
      maxDiscardedFactor = PosZDouble(5),
      minSize = PosZInt(0),
      sizeRange = PosZInt(100),
      workers = PosInt(8)
    )

  /**
    * Checks that the strategy can handle the given scenario.
    *
    * @param name the name of the test.
    * @param run run the effect to get assertion result.
    * @tparam F the effect in which the simulation is run.
    * @tparam Strat the strategy to use.
    * @tparam S the scenario.
    */
  final def simulate[F[_]: Functor, Strat[_[_]], S <: Scenario: Arbitrary](
      name: String
  )(run: F[Assertion] => Assertion)(
      implicit strategy: ArbitraryStrategy[Strat[F]],
      ev: Strat[F] <:< Strategy[F],
      M: Monoid[F[PostResolution]]
  ): Unit =
    name in {
      forAll { simulation: Simulation[F, Strat, S] =>
        run(simulation.splitBrainResolved.map(_ shouldBe true))
      }
    }

  final def simulateWithNonCleanPartitions[F[_]: Applicative, Strat[_[_]], S <: Scenario: Arbitrary](
      name: String
  )(run: F[Assertion] => Assertion)(
      implicit strategy: ArbitraryStrategy[Strat[F]],
      ev: Strat[F] <:< Strategy[F],
      M: Monoid[F[PostResolution]]
  ): Unit =
    simulate[F, Union[?[_], Strat, IndirectlyConnected], WithNonCleanPartitions[S]](name)(run)
}
