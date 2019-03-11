package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.Member
import akka.cluster.sbr.{MySpec, Reachable, Unreachable, WorldView}
import akka.cluster.sbr.strategies.keepoldest.ArbitraryInstances._
import akka.cluster.sbr.strategies.keepoldest.KeepOldest.Config
import cats.implicits._

class KeepOldestViewSpec extends MySpec {
  "KeepOldestView" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, config: Config) =>
        val maybeOldestNode = worldView.m.toList.sortBy(_._1)(Member.ageOrdering).headOption

        KeepOldestView(worldView, config)
          .map {
            case OldestReachable =>
              maybeOldestNode.fold(fail) { oldestNode =>
                oldestNode._2 shouldBe Reachable
                if (config.downIfAlone) worldView.reachableNodes.size should be > 1
                else succeed
              }
            case OldestAlone =>
              maybeOldestNode.fold(fail) { oldestNode =>
                oldestNode._2 shouldBe Reachable
                if (config.downIfAlone) worldView.reachableNodes.size shouldEqual 1
                else succeed
              }
            case OldestUnreachable => maybeOldestNode.fold(fail)(_._2 shouldBe Unreachable)
          }
          .fold(_ => if (worldView.allNodes.isEmpty) succeed else fail, identity)
      }
    }
  }
}
