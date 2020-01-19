package com.swissborg.lithium

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.swissborg.TestMember
import com.swissborg.lithium.WorldView.Status
import com.swissborg.lithium.testImplicits._
import com.swissborg.lithium.reachability._

import scala.collection.immutable.SortedSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WorldViewSuite extends AnyWordSpec with Matchers {
  val aa = TestMember(Address("akka", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka", "sys", "c", 2552), Up)
  val dd = TestMember(Address("akka", "sys", "d", 2552), Up)
  val ee = TestMember(Address("akka", "sys", "e", 2552), Up)

  val aa0 = TestMember(Address("akka", "sys", "a", 2552), Removed)

  val joining  = TestMember(Address("akka", "sys", "joining", 2552), Joining)
  val weaklyUp = TestMember(Address("akka", "sys", "weaklyUp", 2552), WeaklyUp)
  val up       = TestMember(Address("akka", "sys", "up", 2552), Up)
  val leaving  = TestMember(Address("akka", "sys", "leaving", 2552), Leaving)
  val exiting  = TestMember(Address("akka", "sys", "exiting", 2552), Exiting)
  val down     = TestMember(Address("akka", "sys", "down", 2552), Down)
  val removed  = TestMember(Address("akka", "sys", "removed", 2552), Removed)

  val otherDC     = TestMember(Address("akka", "sys", "other-dc", 2552), Up, "Other")
  val bbInOtherDC = TestMember(Address("akka", "sys", "b", 2552), Removed, "Other")

  "WorldView" must {
    "init" in {
      val w = WorldView.init(aa)
      w.members should ===(Set(aa))
      w.reachableNodes.map(_.member) should ===(Set(aa))
    }

    "build from a snapshot" in {
      val w = WorldView.fromSnapshot(aa,
                                     CurrentClusterState(SortedSet(aa, bb, cc, dd, removed),
                                                         Set(dd),
                                                         seenBy = Set(aa.address, bb.address, cc.address)))

      w.reachableNodes.map(_.member) should ===(Set(aa, bb, cc))
      w.unreachableNodes.map(_.member) should ===(Set(dd))
      w.indirectlyConnectedNodes should ===(Set.empty[Node])
    }

    "build from nodes" in {
      val w = WorldView.fromNodes(
        IndirectlyConnectedNode(aa),
        SortedSet(ReachableNode(bb), IndirectlyConnectedNode(cc), UnreachableNode(dd), ReachableNode(removed))
      )

      w.reachableNodes.map(_.member) should ===(Set(bb))
      w.unreachableNodes.map(_.member) should ===(Set(dd))
      w.indirectlyConnectedNodes.map(_.member) should ===(Set(aa, cc))
    }

    "correctly classify nodes from a snapshot" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set(aa.address, bb.address))
      )

      w.reachableNodes.map(_.member) should ===(Set(aa, bb))
      w.unreachableNodes.map(_.member) should ===(Set(cc))
    }

    "add a member as reachable when getting the first member event" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set(aa.address, bb.address))
      )

      val w1 = w.addOrUpdate(joining)
      w1.reachableNodes.map(_.member) should ===(Set(aa, bb, joining))

      val w2 = w.addOrUpdate(weaklyUp)
      w2.reachableNodes.map(_.member) should ===(Set(aa, bb, weaklyUp))

      val w3 = w.addOrUpdate(up)
      w3.reachableNodes.map(_.member) should ===(Set(aa, bb, up))

      val w4 = w.addOrUpdate(leaving)
      w4.reachableNodes.map(_.member) should ===(Set(aa, bb, leaving))

      val w5 = w.addOrUpdate(exiting)
      w5.reachableNodes.map(_.member) should ===(Set(aa, bb, exiting))

      val w6 = w.addOrUpdate(down)
      w6.reachableNodes.map(_.member) should ===(Set(aa, bb, down))
    }

    "move a member from reachable to unreachable" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), seenBy = Set.empty))

      val w2 = w.withUnreachableNode(bb.uniqueAddress)
      w2.reachableNodes.map(_.member) should ===(Set(aa))
      w2.unreachableNodes.map(_.member) should ===(Set(bb))
    }

    "self member must not become unreachable" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), seenBy = Set.empty))

      val w1 = w.withUnreachableNode(aa.uniqueAddress)
      w1.reachableNodes.map(_.member) should ===(Set(aa, bb))
      w1.unreachableNodes.map(_.member) should ===(Set.empty)
    }

    "move a member from unreachable to reachable" in {
      val w1 = WorldView
        .fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), Set(bb), seenBy = Set.empty))
        .withReachableNode(bb.uniqueAddress)

      w1.reachableNodes.map(_.member) should ===(Set(aa, bb))
      w1.unreachableNodes.map(_.member) should ===(Set.empty)

      val w2 = WorldView
        .fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), Set(aa), seenBy = Set(aa.address, bb.address)))
        .withReachableNode(aa.uniqueAddress)

      w2.reachableNodes.map(_.member) should ===(Set(aa, bb))
      w2.unreachableNodes.map(_.member) should ===(Set.empty)
    }

    "move a member from reachable to indirectly connected" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address)))

      // todo should selfMember really become unreachable
      val w1 = w.withIndirectlyConnectedNode(aa.uniqueAddress)
      w1.reachableNodes.map(_.member) should ===(Set(bb))
      w1.indirectlyConnectedNodes.map(_.member) should ===(Set(aa))

      val w2 = w.withIndirectlyConnectedNode(bb.uniqueAddress)
      w2.reachableNodes.map(_.member) should ===(Set(aa))
      w2.indirectlyConnectedNodes.map(_.member) should ===(Set(bb))
    }

    "move a member from indirectly connected to reachable" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address)))

      val w1 = w.withIndirectlyConnectedNode(aa.uniqueAddress).withReachableNode(aa.uniqueAddress)
      w1.reachableNodes.map(_.member) should ===(Set(aa, bb))

      val w2 = w.withIndirectlyConnectedNode(bb.uniqueAddress).withReachableNode(bb.uniqueAddress)
      w2.reachableNodes.map(_.member) should ===(Set(aa, bb))
    }

    "move a member from unreachable to indirectly connected" in {
      val w1 = WorldView
        .fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), Set(bb), seenBy = Set(aa.address, bb.address)))
        .withIndirectlyConnectedNode(bb.uniqueAddress)

      w1.reachableNodes.map(_.member) should ===(Set(aa))
      w1.indirectlyConnectedNodes.map(_.member) should ===(Set(bb))

      val w2 = WorldView
        .fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), Set(aa), seenBy = Set(aa.address, bb.address)))
        .withIndirectlyConnectedNode(aa.uniqueAddress)

      w2.reachableNodes.map(_.member) should ===(Set(bb))
      w2.indirectlyConnectedNodes.map(_.member) should ===(Set(aa))
    }

    "move a member from indirectly connected to unreachable" in {
      val w1 = WorldView
        .fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), seenBy = Set.empty))
        .withIndirectlyConnectedNode(bb.uniqueAddress)
        .withUnreachableNode(bb.uniqueAddress)

      w1.reachableNodes.map(_.member) should ===(Set(aa))
      w1.unreachableNodes.map(_.member) should ===(Set(bb))
    }

    "remove an unreachable member" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, removed), Set(removed), seenBy = Set(aa.address, bb.address))
      )

      val w1 = w.removeMember(removed)
      w1.nodes.map(_.member) should ===(Set(aa, bb))
    }

    "remove a reachable member" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, removed, cc), Set(cc), seenBy = Set(aa.address, bb.address))
      )

      val w1 = w.removeMember(removed)
      w1.nodes.map(_.member) should ===(Set(aa, cc))
    }

    "remove an indirectly connected member" in {
      val w = WorldView
        .fromSnapshot(aa,
                      CurrentClusterState(SortedSet(aa, removed, cc), Set(cc), seenBy = Set(aa.address, bb.address)))
        .withIndirectlyConnectedNode(removed.uniqueAddress)

      val w1 = w.removeMember(removed)
      w1.nodes.map(_.member) should ===(Set(aa, cc))
    }

    "remove an unknown member" in {
      val w = WorldView
        .fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address)))
        .removeMember(removed)

      w.members should ===(Set(aa, bb))
    }

    "update self" in {
      val w = WorldView
        .fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address)))
        .addOrUpdate(aa.copy(Leaving))

      w.selfStatus should ===(Status(aa.copy(Leaving), ReachabilityStatus.Reachable))
    }

    "get the latest state of selfMember from the snapshot" in {
      val w = WorldView.fromSnapshot(aa0,
                                     CurrentClusterState(SortedSet(aa, bb, cc, dd, removed),
                                                         Set(dd),
                                                         seenBy = Set(aa.address, bb.address, cc.address)))

      w.selfStatus.member.status should ===(aa.status)
    }

    "use the provided selfMember if not in the snapshot" in {
      val w = WorldView.fromSnapshot(aa0, CurrentClusterState(SortedSet(bb, cc, dd, removed), Set(dd)))

      w.selfStatus.member.status should ===(aa0.status)
    }

    "ignore members from another datacenter" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb)))
      w.addOrUpdate(otherDC) should ===(w)
      w.removeMember(bbInOtherDC) should ===(w)
    }

    "ignore members from another data-center when building from a snapshot" in {
      WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, otherDC), SortedSet(otherDC))) should ===(
        WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa)))
      )
    }

    "ignore members from another data-center when building from nodes" in {
      WorldView.fromNodes(ReachableNode(aa), SortedSet(UnreachableNode(otherDC))) should ===(
        WorldView.fromNodes(ReachableNode(aa), SortedSet.empty)
      )
    }
  }
}
