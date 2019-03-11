package akka.cluster.sbr.strategies.keepreferee

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Gen.posNum

object ArbitraryInstances extends akka.cluster.sbr.ArbitraryInstances {
  implicit val arbPositiveInt: Arbitrary[Int Refined Positive] = Arbitrary {
    posNum[Int].map(refineV[Positive](_).right.get) // trust me
  }
}
