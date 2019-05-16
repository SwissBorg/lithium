package com.swissborg.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.UniqueAddress
import cats.implicits._

class WorldViewSpec extends SBSpec {
  "WorldView" must {
    "not have a node simultaneously reachable and unreachable" in {
      forAll { worldView: WorldView =>
        worldView.consideredReachableNodes
          .map(_.member)
          .intersect(worldView.unreachableNodes.map(_.member)) shouldBe empty
      }
    }

    "memberEvent" in {
      forAll { (worldView: WorldView, event: MemberEvent, seenBy: Set[Address]) =>
        event match {
          case MemberRemoved(member, _) =>
            val w = worldView.removeMember(member, seenBy)
            if (w.selfUniqueAddress != member.uniqueAddress) {
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
        }
      }
    }

    "reachabilityEvent" in {
      forAll { (worldView: WorldView, event: ReachabilityEvent) =>
        event match {
          case UnreachableMember(member) =>
            val w = worldView.withUnreachableMember(member)
            w.nodes.contains(UnreachableNode(member)) shouldBe true

          case ReachableMember(member) =>
            val w = worldView.withReachableMember(member)
            w.nodes.contains(ReachableNode(member)) shouldBe true
        }
      }
    }

    "reachableConsideredNodes" in {
      forAll { worldView: WorldView =>
        assert(worldView.consideredReachableNodes.forall(worldView.nodes.contains))
      }
    }

    "unreachableNodes" in {
      forAll { worldView: WorldView =>
        assert(worldView.unreachableNodes.forall(worldView.nodes.contains))

      }
    }

    "consideredNodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.consideredNodes.forall((worldView.consideredReachableNodes ++ worldView.unreachableNodes).contains)
        )
      }
    }

    "consideredNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role.isEmpty) worldView.consideredNodesWithRole(role) should ===(worldView.consideredNodes)
        else assert(worldView.consideredNodesWithRole(role).forall(worldView.consideredNodes.contains))

        worldView.consideredNodesWithRole(role) should ===(
          worldView.consideredReachableNodesWithRole(role) ++ worldView.consideredUnreachableNodesWithRole(role)
        )
      }
    }

    "reachableNodesWithRole" in {
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

    "otherStatuses should not contain the self node" in {
      forAll { worldView: WorldView =>
        worldView.otherMembersStatus.contains(worldView.selfUniqueAddress) shouldBe false
      }
    }

    "remove all removed members" in {
      forAll { worldView: WorldView =>
        worldView.pruneRemoved.removedMembers should ===(Set.empty[UniqueAddress])
      }
    }
  }
}
