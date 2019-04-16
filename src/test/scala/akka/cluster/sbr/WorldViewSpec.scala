package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.MemberStatus._
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.implicits._

class WorldViewSpec extends MySpec {
  "WorldView" - {
    "1 - should not have a node simultaneously reachable and unreachable" in {
      forAll { worldView: WorldView =>
        worldView.consideredReachableNodes
          .map(_.member)
          .intersect(worldView.unreachableNodes.map(_.member)) shouldBe empty
      }
    }

    "2 - memberEvent" in {
      forAll { (worldView: WorldView, event: MemberEvent) =>
        event match {
          case MemberJoined(member) =>
            val w = worldView.memberEvent(event)

            if (worldView.nodes.find(_.member === member).nonEmpty) {
              w.nodes.exists { node =>
                node.member === member && node.member.status == Joining
              } shouldBe true
            } else {
              w.nodes.contains(ReachableNode(member)) shouldBe true
            }

            w.consideredNodes.exists(_.member === member) shouldBe false

          case MemberWeaklyUp(member) =>
            val w = worldView.memberEvent(event)

            if (worldView.nodes.find(_.member === member).nonEmpty) {
              w.nodes.exists { node =>
                node.member === member && node.member.status == WeaklyUp
              } shouldBe true
            } else {
              w.nodes.contains(ReachableNode(member)) shouldBe true
            }

            w.consideredNodes.exists(_.member === member) shouldBe false

          case MemberLeft(member) =>
            val w = worldView.memberEvent(event)

            if (worldView.nodes.find(_.member === member).nonEmpty) {
              w.nodes.exists { node =>
                node.member === member && node.member.status == Leaving
              } shouldBe true
            } else {
              w.nodes.contains(ReachableNode(member)) shouldBe true
            }

            w.consideredNodes.exists(n => cats.Eq[Member].eqv(n.member, member)) shouldBe true

          case MemberExited(member) =>
            val w = worldView.memberEvent(event)

            if (worldView.nodes.find(_.member === member).nonEmpty) {
              w.nodes.exists { node =>
                node.member === member && node.member.status == Exiting
              } shouldBe true
            } else {
              w.nodes.contains(ReachableNode(member)) shouldBe true
            }

            w.consideredNodes.exists(_.member === member) shouldBe true

          case MemberDowned(member) =>
            val w = worldView.memberEvent(event)

            if (worldView.nodes.find(_.member === member).nonEmpty) {
              w.nodes.exists { node =>
                node.member === member && node.member.status == Down
              } shouldBe true
            } else {
              w.nodes.contains(ReachableNode(member)) shouldBe true
            }

            w.consideredNodes.exists(_.member === member) shouldBe true

          case MemberRemoved(member, _) =>
            val w = worldView.memberEvent(event)
            if (w.selfNode.member !== member) {
              w.nodes.find(_.member === member).isEmpty shouldBe true
            } else {
              w.selfNode.member should ===(member)
            }

          case MemberUp(member) =>
            val w = worldView.memberEvent(event)

            if (worldView.nodes.find(_.member === member).nonEmpty) {
              w.nodes.exists { node =>
                node.member === member && node.member.status == Up
              } shouldBe true
            } else {
              w.nodes.contains(ReachableNode(member)) shouldBe true
            }

            w.consideredNodes.exists(_.member === member) shouldBe true
        }
      }
    }

    "3 - reachabilityEvent" in {
      forAll { (worldView: WorldView, event: ReachabilityEvent) =>
        event match {
          case UnreachableMember(member) =>
            val w = worldView.reachabilityEvent(event)
            w.nodes.contains(UnreachableNode(member)) shouldBe true

          case ReachableMember(member) =>
            val w = worldView.reachabilityEvent(event)
            w.nodes.contains(ReachableNode(member)) shouldBe true
        }
      }
    }

    "4 - reachableConsideredNodes" in {
      forAll { worldView: WorldView =>
        assert(worldView.consideredReachableNodes.forall(worldView.nodes.contains))
      }
    }

    "5 - unreachableNodes" in {
      forAll { worldView: WorldView =>
        assert(worldView.unreachableNodes.forall(worldView.nodes.contains))

      }
    }

    "6 - consideredNodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.consideredNodes.forall((worldView.consideredReachableNodes ++ worldView.unreachableNodes).contains)
        )
      }
    }

    "7 - consideredNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role.isEmpty) worldView.consideredNodesWithRole(role) should ===(worldView.consideredNodes)
        else assert(worldView.consideredNodesWithRole(role).forall(worldView.consideredNodes.contains))

        worldView.consideredNodesWithRole(role) should ===(
          worldView.consideredReachableNodesWithRole(role) ++ worldView.consideredUnreachableNodesWithRole(role)
        )
      }
    }

    "8 - reachableNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role === "")
          worldView.consideredReachableNodesWithRole(role).map(_.member) should ===(
            worldView.consideredReachableNodes.map(_.member)
          )
        else
          assert(
            worldView
              .consideredReachableNodesWithRole(role)
              .forall(worldView.consideredReachableNodes.contains)
          )
      }
    }

    "9 - unreachableNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role === "")
          worldView
            .consideredUnreachableNodesWithRole(role)
            .map(_.member) should ===(worldView.unreachableNodes.map(_.member))
        else assert(worldView.consideredUnreachableNodesWithRole(role).forall(worldView.unreachableNodes.contains))
      }
    }

    "10 - otherStatuses should not contain the self node" in {
      forAll { worldView: WorldView =>
        worldView.otherNodes.contains(worldView.selfNode) shouldBe false
      }
    }
  }
}
