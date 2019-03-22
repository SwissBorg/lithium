package akka.cluster.sbr.strategies.keepoldest

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._

object ArbitraryInstances extends akka.cluster.sbr.ArbitraryInstances {
  implicit val arbKeepOldest: Arbitrary[KeepOldest] = Arbitrary {
    for {
      downIfAlone <- arbitrary[Boolean]
      role        <- arbitrary[String]
    } yield KeepOldest(downIfAlone, role)
  }
}
