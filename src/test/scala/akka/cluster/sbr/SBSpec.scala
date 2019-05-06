package akka.cluster.sbr

import org.scalactic.anyvals._
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

trait SBSpec extends FreeSpec with Matchers with ScalaCheckPropertyChecks {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = PosInt(1000),
                               maxDiscardedFactor = PosZDouble(5),
                               minSize = PosZInt(0),
                               sizeRange = PosZInt(100),
                               workers = PosInt(8))
}
