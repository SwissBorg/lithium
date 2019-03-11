package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr.strategies.keepreferee.ArbitraryInstances._
import akka.cluster.sbr.strategies.keepreferee.KeepReferee.Config
import akka.cluster.sbr.{MySpec, WorldView}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

class KeepRefereeViewSpec extends MySpec {
  "KeepRefereeView" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, downAllIfLessThanNodes: Int Refined Positive) =>
        whenever(worldView.allNodes.nonEmpty) {
          val config = Config(worldView.allNodes.take(1).head.address.toString, downAllIfLessThanNodes)

          KeepRefereeView(worldView, config) match {
            case RefereeReachable =>
              worldView.reachableNodes.size should be >= downAllIfLessThanNodes.value
              worldView.reachableNodes.find(_.node.address.toString == config.address) shouldBe defined

            case TooFewReachableNodes =>
              worldView.reachableNodes.size should be < downAllIfLessThanNodes.value
              worldView.reachableNodes.find(_.node.address.toString == config.address) shouldBe defined

            case RefereeUnreachable =>
              worldView.reachableNodes.find(_.node.address.toString == config.address) shouldBe empty
          }
        }
      }
    }
  }
}
