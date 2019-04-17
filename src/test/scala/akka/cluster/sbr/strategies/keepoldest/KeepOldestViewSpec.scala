package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.Member
import akka.cluster.sbr.strategies.keepoldest.ArbitraryInstances._
import akka.cluster.sbr.{MySpec, ReachableNode, UnreachableNode, WorldView}

class KeepOldestViewSpec extends MySpec {
  "KeepOldestView" - {
    "1 - should instantiate the correct instance" in {
      forAll { (worldView: WorldView, keepOldest: KeepOldest) =>
        val maybeOldestNode =
          worldView.consideredNodesWithRole(keepOldest.role).toList.sortBy(_.member)(Member.ageOrdering).headOption

        KeepOldestView(worldView, keepOldest.downIfAlone, keepOldest.role)
          .map {
            case OldestReachable =>
              maybeOldestNode.fold(fail) { oldestNode =>
                oldestNode should ===(ReachableNode(oldestNode.member))
                if (keepOldest.downIfAlone) worldView.consideredReachableNodes.size should be > 1
                else succeed
              }
            case OldestAlone =>
              maybeOldestNode.fold(fail) { oldestNode =>
                oldestNode should ===(ReachableNode(oldestNode.member))
                if (keepOldest.downIfAlone) worldView.consideredReachableNodes.size should ===(1)
                else succeed
              }
            case OldestUnreachable => maybeOldestNode.fold(fail)(o => o should ===(UnreachableNode(o.member)))
          }
          .fold(_ => if (worldView.consideredNodesWithRole(keepOldest.role).isEmpty) succeed else fail, identity)
      }
    }
  }
}
