package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.sbr.strategies.keepoldest.KeepOldest.Config
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._

object ArbitraryInstances extends akka.cluster.sbr.ArbitraryInstances {
  implicit val arbConfig: Arbitrary[Config] = Arbitrary {
    for {
      downIfAlone <- arbitrary[Boolean]
      role        <- arbitrary[String]
    } yield Config(downIfAlone, role)
  }
}
