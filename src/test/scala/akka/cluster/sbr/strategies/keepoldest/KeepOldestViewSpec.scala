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
        val maybeOldestNode = worldView.statuses.toList
          .filter { case (node, _) => if (config.role != "") node.roles.contains(config.role) else true }
          .sortBy(_._1)(Member.ageOrdering)
          .headOption

        KeepOldestView(worldView, config.downIfAlone, config.role)
          .map {
            case OldestReachable =>
              maybeOldestNode.fold(fail) { oldestNode =>
                oldestNode._2 shouldBe Reachable
                if (config.downIfAlone) worldView.reachableConsideredNodes.size should be > 1
                else succeed
              }
            case OldestAlone =>
              maybeOldestNode.fold(fail) { oldestNode =>
                oldestNode._2 shouldBe Reachable
                if (config.downIfAlone) worldView.reachableConsideredNodes.size shouldEqual 1
                else succeed
              }
            case OldestUnreachable => maybeOldestNode.fold(fail)(_._2 shouldBe Unreachable)
          }
          .fold(_ => if (worldView.allConsideredNodesWithRole(config.role).isEmpty) succeed else fail, identity)
      }
    }
  }
}
