package com.swissborg.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.swissborg.TestMember
import com.swissborg.sbr.WorldView.Status
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.reachability.SBReachabilityReporter.SBReachabilityStatus._
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.SortedSet

class WorldViewSuite extends WordSpec with Matchers {
  val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up)
  val dd = TestMember(Address("akka.tcp", "sys", "d", 2552), Up)
  val ee = TestMember(Address("akka.tcp", "sys", "e", 2552), Up)

  val aa0 = TestMember(Address("akka.tcp", "sys", "a", 2552), Removed)

  val joining = TestMember(Address("akka.tcp", "sys", "joining", 2552), Joining)
  val weaklyUp = TestMember(Address("akka.tcp", "sys", "weaklyUp", 2552), WeaklyUp)
  val up = TestMember(Address("akka.tcp", "sys", "up", 2552), Up)
  val leaving = TestMember(Address("akka.tcp", "sys", "leaving", 2552), Leaving)
  val exiting = TestMember(Address("akka.tcp", "sys", "exiting", 2552), Exiting)
  val down = TestMember(Address("akka.tcp", "sys", "down", 2552), Down)
  val removed = TestMember(Address("akka.tcp", "sys", "removed", 2552), Removed)

  "WorldView" must {
    "init" in {
      val w = WorldView.init(aa)
      w.members should ===(Set(aa))
      w.reachableNodes.map(_.member) should ===(Set(aa))
    }

    "build from a snapshot" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(
          SortedSet(aa, bb, cc, dd, removed),
          Set(dd),
          seenBy = Set(aa.address, bb.address, cc.address)
        )
      )

      w.reachableNodes.map(_.member) should ===(Set(aa, bb, cc))
      w.unreachableNodes.map(_.member) should ===(Set(dd))
      w.indirectlyConnectedNodes should ===(Set.empty[Node])
    }

    "build from nodes" in {
      val w = WorldView.fromNodes(
        IndirectlyConnectedNode(aa),
        SortedSet(
          ReachableNode(bb),
          IndirectlyConnectedNode(cc),
          UnreachableNode(dd),
          ReachableNode(removed)
        )
      )

      w.reachableNodes.map(_.member) should ===(Set(bb))
      w.unreachableNodes.map(_.member) should ===(Set(dd))
      w.indirectlyConnectedNodes.map(_.member) should ===(Set(aa, cc))
    }

    "correctly classify nodes from a snapshot" in {
      val w = WorldView
        .fromSnapshot(
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
      val w = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address))
        )

      // todo should selfMember really become unreachable
      val w1 = w.withUnreachableNode(aa.uniqueAddress)
      w1.reachableNodes.map(_.member) should ===(Set(bb))
      w1.unreachableNodes.map(_.member) should ===(Set(aa))

      val w2 = w.withUnreachableNode(bb.uniqueAddress)
      w2.reachableNodes.map(_.member) should ===(Set(aa))
      w2.unreachableNodes.map(_.member) should ===(Set(bb))
    }

    "move a member from unreachable to reachable" in {
      val w1 = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), Set(bb), seenBy = Set(aa.address, bb.address))
        )
        .withReachableNode(bb.uniqueAddress)

      w1.reachableNodes.map(_.member) should ===(Set(aa, bb))
      w1.unreachableNodes.map(_.member) should ===(Set.empty)

      val w2 = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), Set(aa), seenBy = Set(aa.address, bb.address))
        )
        .withReachableNode(aa.uniqueAddress)

      w2.reachableNodes.map(_.member) should ===(Set(aa, bb))
      w2.unreachableNodes.map(_.member) should ===(Set.empty)
    }

    "move a member from reachable to indirectly connected" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address))
        )

      // todo should selfMember really become unreachable
      val w1 = w.withIndirectlyConnectedNode(aa.uniqueAddress)
      w1.reachableNodes.map(_.member) should ===(Set(bb))
      w1.indirectlyConnectedNodes.map(_.member) should ===(Set(aa))

      val w2 = w.withIndirectlyConnectedNode(bb.uniqueAddress)
      w2.reachableNodes.map(_.member) should ===(Set(aa))
      w2.indirectlyConnectedNodes.map(_.member) should ===(Set(bb))
    }

    "move a member from indirectly connected to reachable" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address))
        )

      val w1 = w.withIndirectlyConnectedNode(aa.uniqueAddress).withReachableNode(aa.uniqueAddress)
      w1.reachableNodes.map(_.member) should ===(Set(aa, bb))

      val w2 = w.withIndirectlyConnectedNode(bb.uniqueAddress).withReachableNode(bb.uniqueAddress)
      w2.reachableNodes.map(_.member) should ===(Set(aa, bb))
    }

    "move a member from unreachable to indirectly connected" in {
      val w1 = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), Set(bb), seenBy = Set(aa.address, bb.address))
        )
        .withIndirectlyConnectedNode(bb.uniqueAddress)

      w1.reachableNodes.map(_.member) should ===(Set(aa))
      w1.indirectlyConnectedNodes.map(_.member) should ===(Set(bb))

      val w2 = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), Set(aa), seenBy = Set(aa.address, bb.address))
        )
        .withIndirectlyConnectedNode(aa.uniqueAddress)

      w2.reachableNodes.map(_.member) should ===(Set(bb))
      w2.indirectlyConnectedNodes.map(_.member) should ===(Set(aa))
    }

    "move a member from indirectly connected to unreachable" in {
      val w1 = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address))
        )
        .withIndirectlyConnectedNode(bb.uniqueAddress)
        .withUnreachableNode(bb.uniqueAddress)

      w1.reachableNodes.map(_.member) should ===(Set(aa))
      w1.unreachableNodes.map(_.member) should ===(Set(bb))

      val w2 = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), Set(aa), seenBy = Set(aa.address, bb.address))
        )
        .withIndirectlyConnectedNode(aa.uniqueAddress)
        .withUnreachableNode(aa.uniqueAddress)

      w2.reachableNodes.map(_.member) should ===(Set(bb))
      w2.unreachableNodes.map(_.member) should ===(Set(aa))
    }

    "remove an unreachable member" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(
          SortedSet(aa, bb, removed),
          Set(removed),
          seenBy = Set(aa.address, bb.address)
        )
      )

      val w1 = w.removeMember(removed)
      w1.nodes.map(_.member) should ===(Set(aa, bb))
    }

    "remove a reachable member" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(
          SortedSet(aa, removed, cc),
          Set(cc),
          seenBy = Set(aa.address, bb.address)
        )
      )

      val w1 = w.removeMember(removed)
      w1.nodes.map(_.member) should ===(Set(aa, cc))
    }

    "remove an indirectly connected member" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(
            SortedSet(aa, removed, cc),
            Set(cc),
            seenBy = Set(aa.address, bb.address)
          )
        )
        .withIndirectlyConnectedNode(removed.uniqueAddress)

      val w1 = w.removeMember(removed)
      w1.nodes.map(_.member) should ===(Set(aa, cc))
    }

    "remove an unknown member" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address))
        )
        .removeMember(removed)

      w.members should ===(Set(aa, bb))
    }

    "consider non-joining, non-unreachable, and non-removed nodes" in {
      val w = WorldView
        .fromSnapshot(
          up,
          CurrentClusterState(
            SortedSet(joining, weaklyUp, up, leaving, exiting, down, removed),
            seenBy = Set.empty
          )
        )
      w.nonJoiningNodes.map(_.member) should ===(Set(up, leaving, exiting, down))
    }

    "update self" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address))
        )
        .addOrUpdate(aa.copy(Leaving))

      w.selfStatus should ===(Status(aa.copy(Leaving), Reachable))
    }

    "get the latest state of selfMember from the snapshot" in {
      val w = WorldView.fromSnapshot(
        aa0,
        CurrentClusterState(
          SortedSet(aa, bb, cc, dd, removed),
          Set(dd),
          seenBy = Set(aa.address, bb.address, cc.address)
        )
      )

      w.selfStatus.member.status should ===(aa.status)
    }

    "use the provided selfMember if not in the snapshot" in {
      val w = WorldView.fromSnapshot(
        aa0,
        CurrentClusterState(SortedSet(bb, cc, dd, removed), Set(dd))
      )

      w.selfStatus.member.status should ===(aa0.status)
    }
  }
}
