package akka.cluster.sbr

import org.scalactic.anyvals._
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

trait MySpec extends FreeSpec with Matchers with ScalaCheckPropertyChecks {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
//    PropertyCheckConfig(minSuccessful = 100, maxDiscarded = 5000, maxSize = 100, workers = 8)
    PropertyCheckConfiguration(minSuccessful = PosInt(1000),
      maxDiscardedFactor = PosZDouble(5),
      minSize = PosZInt(0),
      sizeRange = PosZInt(100),
      workers = PosInt(8))

//    PropertyCheckConfiguration(minSuccessful = PosInt(10000),
//                               maxDiscardedFactor = PosZDouble(5),
//                               minSize = PosZInt(100),
//                               sizeRange = PosZInt(100),
//                               workers = PosInt(8))
//    PropertyCheckConfig(minSuccessful = 5000, maxDiscarded = 250000, maxSize = 100, workers = 8)
//    PropertyCheckConfig(minSuccessful = 100)
}
