package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.strategies.keepreferee.KeepReferee.Config
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
        whenever(worldView.allNodes.nonEmpty) {
          implicit val _: Arbitrary[Config] = arbConfig(worldView)

          forAll { config: Config =>
            KeepRefereeView(worldView, config) match {
              case RefereeReachable =>
                worldView.reachableNodes.size should be >= config.downAllIfLessThanNodes.value
                worldView.reachableNodes.find(_.node.address.toString == config.address) shouldBe defined

              case TooFewReachableNodes =>
                worldView.reachableNodes.size should be < config.downAllIfLessThanNodes.value
                worldView.reachableNodes.find(_.node.address.toString == config.address) shouldBe defined

              case RefereeUnreachable =>
                worldView.reachableNodes.find(_.node.address.toString == config.address) shouldBe empty
            }
          }
        }
      }
    }
  }
}

object KeepRefereeViewSpec {
  // TODO pick arbitrary node?
  def arbConfig(worldView: WorldView): Arbitrary[Config] = Arbitrary {
    posNum[Int].map { downAllIfLessThanNodes =>
      Config(worldView.allNodes.take(1).head.address.toString, refineV[Positive](downAllIfLessThanNodes).right.get)
    }
  }
}
