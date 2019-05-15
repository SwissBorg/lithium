package com.swissborg.sbr.strategies.keepmajority

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._

object ArbitraryInstances extends com.swissborg.sbr.ArbitraryInstances {
  implicit val arbKeepMajority: Arbitrary[KeepMajority] = Arbitrary(arbitrary[String].map(KeepMajority(_)))
}
