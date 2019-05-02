package akka.cluster.sbr.utils

import akka.cluster.sbr.MySpec
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._
import org.scalacheck.Arbitrary

class UtilSpec extends MySpec {
  "Util" - {
    "1 - splitIn" in {
      forAll { (parts: Int Refined Positive, head: Int, tail: Set[Int]) =>
        val nes = tail + head

        implicit val _: Arbitrary[List[Set[Int]]] = splitIn(parts, nes)

        forAll { res: List[Set[Int]] =>
          if (parts.value >= 0 && parts.value <= (tail.size + 1)) {
            (res should have).length(parts.value.toLong)
            res.fold(Set.empty)(_ ++ _) should ===(nes)
          } else res.head should ===(nes)
        }
      }
    }
  }
}
