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
        whenever(worldView.allConsideredNodes.nonEmpty) {
          implicit val _: Arbitrary[Config] = arbConfig(worldView)

          forAll { config: Config =>
            KeepRefereeView(worldView, config) match {
              case RefereeReachable =>
                worldView.reachableConsideredNodes.size should be >= config.downAllIfLessThanNodes.value
                worldView.reachableConsideredNodes.find(_.node.address.toString == config.address) shouldBe defined

              case TooFewReachableNodes =>
                worldView.reachableConsideredNodes.size should be < config.downAllIfLessThanNodes.value
                worldView.reachableConsideredNodes.find(_.node.address.toString == config.address) shouldBe defined

              case RefereeUnreachable =>
                worldView.reachableConsideredNodes.find(_.node.address.toString == config.address) shouldBe empty
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
      Config(worldView.allConsideredNodes.take(1).head.address.toString, refineV[Positive](downAllIfLessThanNodes).right.get)
    }
  }
}
