package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.{MySpec, WorldView}
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._

class KeepRefereeViewSpec extends MySpec {
  import KeepRefereeViewSpec._

  "KeepRefereeView" - {
    "1 - should instantiate the correct instance" in {
      forAll { worldView: WorldView =>
        whenever(worldView.allConsideredNodes.nonEmpty) {
          implicit val _: Arbitrary[KeepReferee] = arbKeepReferee(worldView)

          forAll { keepReferee: KeepReferee =>
            KeepRefereeView(worldView, keepReferee.address, keepReferee.downAllIfLessThanNodes) match {
              case RefereeReachable =>
                worldView.reachableConsideredNodes.size should be >= keepReferee.downAllIfLessThanNodes.value
                worldView.reachableConsideredNodes.find(_.node.address.toString == keepReferee.address) shouldBe defined

              case TooFewReachableNodes =>
                worldView.reachableConsideredNodes.size should be < keepReferee.downAllIfLessThanNodes.value
                worldView.reachableConsideredNodes.find(_.node.address.toString == keepReferee.address) shouldBe defined

              case RefereeUnreachable =>
                worldView.reachableConsideredNodes.find(_.node.address.toString == keepReferee.address) shouldBe empty
            }
          }
        }
      }
    }
  }
}

object KeepRefereeViewSpec {
  // TODO pick arbitrary node?
  def arbKeepReferee(worldView: WorldView): Arbitrary[KeepReferee] = Arbitrary {
    posNum[Int].map { downAllIfLessThanNodes =>
      KeepReferee(worldView.allConsideredNodes.take(1).head.address.toString,
                  refineV[Positive](downAllIfLessThanNodes).right.get)
    }
  }
}
