package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.WorldView._
import akka.cluster.sbr.implicits._
import cats.kernel.Eq

class WorldViewSpec extends MySpec {
  "WorldView" - {
    "1 - should not have a node simultaneously reachable and unreachable" in {
      forAll { worldView: WorldView =>
        worldView.reachableConsideredNodes
          .map(_.member)
          .intersect(worldView.unreachableNodes.map(_.member)) shouldBe empty
      }
    }

    "2 - memberEvent" in {
      forAll { (worldView: WorldView, event: MemberEvent) =>
        event match {
          case MemberJoined(node) =>
            val w = worldView.memberEvent(event)
            if (worldView.self !== node) {
              w.statusOf(node) should ===(Some(Staged))
              w.allStatuses.contains(node) shouldBe true
              w.reachableConsideredNodes.contains(ReachableConsideredNode(node)) shouldBe false
              w.unreachableNodes.contains(UnreachableNode(node)) shouldBe false
            } else {
              w should ===(worldView)
            }

          case MemberWeaklyUp(node) =>
            val w = worldView.memberEvent(event)
            w.statusOf(node) should ===(Some(WeaklyReachable))
            w.allStatuses.contains(node) shouldBe true
            w.reachableConsideredNodes.contains(ReachableConsideredNode(node)) shouldBe false
            w.unreachableNodes.contains(UnreachableNode(node)) shouldBe false

          case _: MemberLeft | _: MemberExited =>
            val w = worldView.memberEvent(event)
            w should ===(worldView)

          case MemberDowned(_) =>
            val w = worldView.memberEvent(event)
            w should ===(worldView)

          case MemberRemoved(member, _) =>
            val w = worldView.memberEvent(event)
            if (member !== w.self) {
              w.reachableConsideredNodes.contains(ReachableConsideredNode(member)) shouldBe false
              w.unreachableNodes.contains(UnreachableNode(member)) shouldBe false
            }

          case MemberUp(member) =>
            val w = worldView.memberEvent(event)
            w.reachableConsideredNodes.contains(ReachableConsideredNode(member)) shouldBe true
            w.unreachableNodes.contains(UnreachableNode(member)) shouldBe false
        }
      }
    }

    "3 - reachabilityEvent" in {
      forAll { (worldView: WorldView, event: ReachabilityEvent) =>
        event match {
          case UnreachableMember(node) =>
            val w = worldView.reachabilityEvent(event)
            w.statusOf(node) should ===(Some(Unreachable))

          case ReachableMember(node) =>
            val w = worldView.reachabilityEvent(event)
            w.statusOf(node) should ===(Some(Reachable))
        }
      }
    }

    "4 - reachableConsideredNodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.reachableConsideredNodes
            .forall(n => worldView.allStatuses.lookup(n.member).exists(Eq[Status].eqv(_, Reachable)))
        )
      }
    }

    "5 - unreachableNodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.unreachableNodes.forall(
            n => worldView.allStatuses.lookup(n.member).exists(Eq[Status].eqv(_, Unreachable))
          )
        )
      }
    }

    "6 - allNodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.allConsideredNodes
            .forall(
              (worldView.reachableConsideredNodes.map(_.member) ++ worldView.unreachableNodes.map(_.member)).contains
            )
        )
      }
    }

    "7 - allNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role === "") worldView.allConsideredNodesWithRole(role) should ===(worldView.allConsideredNodes)
        else assert(worldView.allConsideredNodesWithRole(role).forall(worldView.allConsideredNodes.contains))

        worldView.allConsideredNodesWithRole(role) should ===(
          worldView
            .reachableConsideredNodesWithRole(role)
            .map(_.member) ++ worldView
            .unreachableNodesWithRole(role)
            .map(_.member)
        )
      }
    }

    "8 - reachableNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role === "")
          worldView.reachableConsideredNodesWithRole(role).map(_.member) should ===(
            worldView.reachableConsideredNodes.map(_.member)
          )
        else
          assert(
            worldView
              .reachableConsideredNodesWithRole(role)
              .forall(worldView.reachableConsideredNodes.contains)
          )
      }
    }

    "9 - unreachableNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role === "")
          worldView
            .unreachableNodesWithRole(role)
            .map(_.member) should ===(worldView.unreachableNodes.map(_.member))
        else assert(worldView.unreachableNodesWithRole(role).forall(worldView.unreachableNodes.contains))
      }
    }

    "10 - otherStatuses should not contain the self node" in {
      forAll { worldView: WorldView =>
        worldView.otherStatuses.contains(worldView.self) shouldBe false
      }
    }
  }
}
