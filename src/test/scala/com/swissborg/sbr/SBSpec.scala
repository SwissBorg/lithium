package com.swissborg.sbr

import cats.tests.StrictCatsEquality
import org.scalactic.anyvals._
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

trait SBSpec
    extends WordSpec
    with Matchers
    with ScalaCheckPropertyChecks
    with StrictCatsEquality
    with ArbitraryInstances {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = PosInt(1000),
                               maxDiscardedFactor = PosZDouble(5),
                               minSize = PosZInt(0),
                               sizeRange = PosZInt(100),
                               workers = PosInt(8))
}
