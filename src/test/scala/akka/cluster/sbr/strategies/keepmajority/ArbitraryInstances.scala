package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.strategies.keepmajority.KeepMajority.Config
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._

object ArbitraryInstances extends akka.cluster.sbr.ArbitraryInstances {
  implicit val arbConfig: Arbitrary[Config] = Arbitrary(arbitrary[String].map(Config(_)))
}
