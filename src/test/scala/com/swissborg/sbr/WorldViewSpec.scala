package com.swissborg.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.Member
import cats.implicits._

class WorldViewSpec extends SBSpec {
  "WorldView" must {
    "not have a node simultaneously reachable and unreachable" in {
      forAll { worldView: WorldView =>
        worldView.nonJoiningReachableNodes
          .map(_.member)
          .intersect(worldView.unreachableNodes.map(_.member)) shouldBe empty
      }
    }

    "change with member events" in {
      forAll { (worldView: WorldView, event: MemberEvent) =>
        event match {
          case MemberRemoved(member, _) =>
            val w = worldView.removeMember(member)
            if (w.selfUniqueAddress != member.uniqueAddress) {
              w.members(member) shouldBe false
            } else {
              // selfMember cannot be removed.
              w.members(member) shouldBe true
            }

          case e =>
            val w = worldView.addOrUpdate(e.member)
            w.members(e.member) shouldBe true
        }
      }
    }

    "change with reachability events" in {
      forAll { (worldView: WorldView, member: Member) =>
        val w = worldView.addOrUpdate(member)
        w.withReachableNode(member.uniqueAddress)
          .nodes
          .contains(ReachableNode(member)) shouldBe true
        w.withIndirectlyConnectedNode(member.uniqueAddress)
          .nodes
          .contains(IndirectlyConnectedNode(member)) shouldBe true

        val w0 = w.withUnreachableNode(member.uniqueAddress) //.nodes.contains(UnreachableNode(member))
        if (w0.selfUniqueAddress != member.uniqueAddress) {
          w0.nodes.contains(UnreachableNode(member)) shouldBe true
        } // TODO what when it's self?
      }
    }

    "get the considered reachable nodes" in {
      forAll { worldView: WorldView =>
        assert(worldView.nonJoiningReachableNodes.forall(worldView.nodes.contains))
      }
    }

    "get the unreachable nodes" in {
      forAll { worldView: WorldView =>
        assert(worldView.unreachableNodes.forall(worldView.nodes.contains))

      }
    }

    "get the considered nodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.nonJoiningNonICNodes.forall(
            (worldView.nonJoiningReachableNodes ++ worldView.unreachableNodes).contains
          )
        )
      }
    }

    "get the indirectly-connected nodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.indirectlyConnectedNodes.forall(worldView.nodes.contains)
        )
      }
    }

    "get the considered nodes with a role" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role.isEmpty)
          worldView.nonJoiningNonICNodesWithRole(role) should ===(worldView.nonJoiningNonICNodes)
        else
          assert(
            worldView
              .nonJoiningNonICNodesWithRole(role)
              .forall(worldView.nonJoiningNonICNodes.contains)
          )

        worldView.nonJoiningNonICNodesWithRole(role) should ===(
          worldView.nonJoiningReachableNodesWithRole(role) ++ worldView
            .nonJoiningUnreachableNodesWithRole(role)
        )
      }
    }

    "get the considered reachable nodes with a role" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role.isEmpty)
          worldView.nonJoiningReachableNodesWithRole(role).map(_.member) should ===(
            worldView.nonJoiningReachableNodes.map(_.member)
          )
        else
          assert(
            worldView
              .nonJoiningReachableNodesWithRole(role)
              .forall(worldView.nonJoiningReachableNodes.contains)
          )
      }
    }

    "get the indirectly-connected nodes with a role" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role.isEmpty)
          worldView.indirectlyConnectedNodesWithRole(role).map(_.member) should ===(
            worldView.indirectlyConnectedNodes.map(_.member)
          )
        else
          assert(
            worldView
              .indirectlyConnectedNodesWithRole(role)
              .forall(worldView.indirectlyConnectedNodes.contains)
          )
      }
    }

    "otherStatuses should not contain the self node" in {
      forAll { worldView: WorldView =>
        worldView.otherMembersStatus.contains(worldView.selfUniqueAddress) shouldBe false
      }
    }
  }
}
