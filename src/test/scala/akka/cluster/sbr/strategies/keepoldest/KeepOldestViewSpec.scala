package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.Member
import akka.cluster.sbr.strategies.keepoldest.ArbitraryInstances._
import akka.cluster.sbr.{MySpec, Reachable, Unreachable, WorldView}

class KeepOldestViewSpec extends MySpec {
  "KeepOldestView" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, keepOldest: KeepOldest) =>
        val maybeOldestNode = worldView.allStatuses.toSortedMap.toList
          .filter { case (node, _) => if (keepOldest.role != "") node.roles.contains(keepOldest.role) else true }
          .sortBy(_._1)(Member.ageOrdering)
          .headOption

        KeepOldestView(worldView, keepOldest.downIfAlone, keepOldest.role)
          .map {
            case OldestReachable =>
              maybeOldestNode.fold(fail) { oldestNode =>
                oldestNode._2 should ===(Reachable)
                if (keepOldest.downIfAlone) worldView.reachableConsideredNodes.size should be > 1
                else succeed
              }
            case OldestAlone =>
              maybeOldestNode.fold(fail) { oldestNode =>
                oldestNode._2 shouldBe Reachable
                if (keepOldest.downIfAlone) worldView.reachableConsideredNodes.size should ===(1)
                else succeed
              }
            case OldestUnreachable => maybeOldestNode.fold(fail)(_._2 should ===(Unreachable))
          }
          .fold(_ => if (worldView.allConsideredNodesWithRole(keepOldest.role).isEmpty) succeed else fail, identity)
      }
    }
  }
}
