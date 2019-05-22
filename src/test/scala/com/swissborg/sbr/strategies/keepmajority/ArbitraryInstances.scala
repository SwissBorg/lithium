package com.swissborg.sbr.strategies.keepmajority

import cats.ApplicativeError
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._

object ArbitraryInstances extends com.swissborg.sbr.ArbitraryInstances {
  implicit def arbKeepMajority[F[_]](implicit F: ApplicativeError[F, Throwable]): Arbitrary[KeepMajority[F]] =
    Arbitrary(arbitrary[String].map(role => new KeepMajority[F](KeepMajority.Config(role))))
}
