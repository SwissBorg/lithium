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
        whenever(worldView.consideredNodes.nonEmpty) {
          implicit val _: Arbitrary[KeepReferee] = arbKeepReferee(worldView)

          forAll { keepReferee: KeepReferee =>
            KeepRefereeView(worldView, keepReferee.address, keepReferee.downAllIfLessThanNodes) match {
              case RefereeReachable =>
                worldView.consideredReachableNodes.size should be >= keepReferee.downAllIfLessThanNodes.value
                worldView.consideredReachableNodes.find(_.member.address.toString == keepReferee.address) shouldBe defined

              case TooFewReachableNodes =>
                worldView.consideredReachableNodes.size should be < keepReferee.downAllIfLessThanNodes.value
                worldView.consideredReachableNodes.find(_.member.address.toString == keepReferee.address) shouldBe defined

              case RefereeUnreachable =>
                worldView.consideredReachableNodes.find(_.member.address.toString == keepReferee.address) shouldBe empty
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
      KeepReferee(worldView.consideredNodes.take(1).head.member.address.toString,
                  refineV[Positive](downAllIfLessThanNodes).right.get)
    }
  }
}
