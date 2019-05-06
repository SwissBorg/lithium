package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.sbr.ArbitraryInstances._

class WorldViewSpec extends SBSpec {
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
          case MemberRemoved(member, _) =>
            val w = worldView.memberRemoved(member, seenBy)
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

    "9 - otherStatuses should not contain the self node" in {
      forAll { worldView: WorldView =>
        worldView.otherMembersStatus.contains(worldView.selfUniqueAddress) shouldBe false
      }
    }

    "10 - remove all removed members" in {
      forAll { worldView: WorldView =>
        worldView.pruneRemoved.removedMembers should ===(Set.empty)
      }
    }
  }
}
