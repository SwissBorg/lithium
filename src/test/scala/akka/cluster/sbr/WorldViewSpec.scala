package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.sbr.ArbitraryInstances._

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
      forAll { (worldView: WorldView, event: MemberEvent, seenBy: Set[Address]) =>
        event match {
//          case MemberJoined(member) =>
//            val w = worldView.updateMember(member, Set.empty)
//
//            if (worldView.members(member)) {
//              w.nodes.exists { node =>
//                node.member == member && node.member.status == Joining
//              } shouldBe true
//            } else {
//              w.nodes.contains(ReachableNode(member)) shouldBe true
//            }
//
//            w.consideredNodes.exists(_.member == member) shouldBe false
//
//          case MemberWeaklyUp(member) =>
//            val w = worldView.updateMember(member, Set.empty)
//
//            if (worldView.members(member)) {
//              w.nodes.exists { node =>
//                node.member == member && node.member.status == WeaklyUp
//              } shouldBe true
//            } else {
//              w.nodes.contains(ReachableNode(member)) shouldBe true
//            }
//
//            w.consideredNodes.exists(_.member == member) shouldBe false
//
//          case MemberLeft(member) =>
//            val w = worldView.updateMember(member, Set.empty)
//
//            if (worldView.members(member)) {
//              w.nodes.exists { node =>
//                node.member == member && node.member.status == Leaving
//              } shouldBe true
//            } else {
//              w.nodes.contains(ReachableNode(member)) shouldBe true
//            }
//
//            w.consideredNodes.exists(n => cats.Eq[Member].eqv(n.member, member)) shouldBe true

          case MemberRemoved(member, _) =>
            val w = worldView.memberRemoved(member, seenBy)
            if (w.selfUniqueAddress != member.uniqueAddress) {
//              println(s"w.members = ${w.members} -> $member")
//              println(s"w.members = ${w.members.map(_.uniqueAddress)(Order.from(_.compare(_)))} -> ${member.uniqueAddress}")
//              println(s"w.members(member) = ${w.members(member)}")
              w.members(member) shouldBe false
              w.removedMembers(member.uniqueAddress) shouldBe true
            } else {
              // selfMember cannot be removed.
              w.members(member) shouldBe true
              w.removedMembers(member.uniqueAddress) shouldBe false
            }

            w.seenBy(member) should ===(seenBy)

          case e =>
            val w = worldView.updateMember(e.member, seenBy)
            w.members(e.member) shouldBe true
            w.seenBy(e.member) should ===(seenBy)

//          case MemberDowned(member) =>
//            val w = worldView.updateMember(member, Set.empty)
//
//            if (worldView.members(member)) {
//              w.nodes.exists { node =>
//                node.member == member && node.member.status == Down
//              } shouldBe true
//            } else {
//              w.nodes.contains(ReachableNode(member)) shouldBe true
//            }
//
//            w.consideredNodes.exists(_.member == member) shouldBe true
//
//          case MemberRemoved(member, _) =>
//            val w = worldView.memberRemoved(member, Set.empty)
//            if (w.selfMember != member) {
//              w.nodes.find(_.member == member).isEmpty shouldBe true
//            } else {
//              w.selfMember should ===(member)
//            }
//
//          case MemberUp(member) =>
//            val w = worldView.updateMember(member, Set.empty)
//
//            if (worldView.members(member)) {
//              w.nodes.exists { node =>
//                node.member == member && node.member.status == Up
//              } shouldBe true
//            } else {
//              w.nodes.contains(ReachableNode(member)) shouldBe true
//            }
//
//            w.consideredNodes.exists(_.member == member) shouldBe true
        }
      }
    }

    "3 - reachabilityEvent" in {
      forAll { (worldView: WorldView, event: ReachabilityEvent) =>
        event match {
          case UnreachableMember(member) =>
            val w = worldView.unreachableMember(member)
            w.nodes.contains(UnreachableNode(member)) shouldBe true

          case ReachableMember(member) =>
            val w = worldView.reachableMember(member)
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
        if (role.isEmpty)
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

//    "9 - unreachableNodesWithRole" in {
//      forAll { (worldView: WorldView, role: String) =>
//        if (role.isEmpty)
//          worldView
//            .consideredUnreachableNodesWithRole(role)
//            .map(_.member) should ===(worldView.unreachableNodes.map(_.member).filter())
//        else assert(worldView.consideredUnreachableNodesWithRole(role).forall(worldView.unreachableNodes.contains))
//      }
//    }

    "10 - otherStatuses should not contain the self node" in {
      forAll { worldView: WorldView =>
        worldView.otherMembersStatus.contains(worldView.selfUniqueAddress) shouldBe false
      }
    }
  }
}
