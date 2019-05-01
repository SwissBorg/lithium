package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.sbr.utils._
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.SortedSet

class WorldViewSuite extends WordSpec with Matchers {
  val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up)
  val dd = TestMember(Address("akka.tcp", "sys", "d", 2552), Up)
  val ee = TestMember(Address("akka.tcp", "sys", "e", 2552), Up)

  val joining  = TestMember(Address("akka.tcp", "sys", "d", 2552), Joining)
  val weaklyUp = TestMember(Address("akka.tcp", "sys", "d", 2552), WeaklyUp)
  val up       = TestMember(Address("akka.tcp", "sys", "d", 2552), Up)
  val leaving  = TestMember(Address("akka.tcp", "sys", "d", 2552), Leaving)
  val exiting  = TestMember(Address("akka.tcp", "sys", "d", 2552), Exiting)
  val down     = TestMember(Address("akka.tcp", "sys", "d", 2552), Down)
  val removed  = TestMember(Address("akka.tcp", "sys", "d", 2552), Removed)

  "SBRFailureDetectorState" must {
    "initialize the seenBy from a snapshot" in {
      val w = WorldView.fromSnapshot(
        aa,
        trackIndirectlyConnected = true,
        CurrentClusterState(SortedSet(aa, bb, cc), Set.empty, seenBy = Set(aa.address, bb.address, cc.address))
      )

      w.wasSeenBy(ReachableNode(aa)) should ===(Set(aa.address, bb.address, cc.address))
      w.wasSeenBy(ReachableNode(bb)) should ===(Set(aa.address, bb.address, cc.address))
      w.wasSeenBy(ReachableNode(cc)) should ===(Set(aa.address, bb.address, cc.address))
    }

    "update the seenBy set when a member is updated" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb, cc), Set.empty, seenBy = Set(aa.address, bb.address, cc.address))
        )
        .memberEvent(MemberLeft(cc.copy(Leaving)), Set(aa.address, bb.address))

      w.wasSeenBy(ReachableNode(aa)) should ===(Set(aa.address, bb.address, cc.address))
      w.wasSeenBy(ReachableNode(bb)) should ===(Set(aa.address, bb.address, cc.address))
      w.wasSeenBy(ReachableNode(cc.copy(Leaving))) should ===(Set(aa.address, bb.address))
    }

    "update all the seenBys" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb, cc), Set.empty, seenBy = Set(aa.address, bb.address, cc.address))
        )
        .seenBy(Set(aa.address, bb.address, cc.address, dd.address))

      w.wasSeenBy(ReachableNode(aa)) should ===(Set(aa.address, bb.address, cc.address, dd.address))
      w.wasSeenBy(ReachableNode(bb)) should ===(Set(aa.address, bb.address, cc.address, dd.address))
      w.wasSeenBy(ReachableNode(cc)) should ===(Set(aa.address, bb.address, cc.address, dd.address))
    }

    "correctly classify nodes from a snapshot" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set(aa.address, bb.address)),
        )

      w.reachableNodes.map(_.member) should ===(Set(aa, bb))
      w.unreachableNodes.map(_.member) should ===(Set(cc))
    }

    "add a member as reachable when getting the first member event" in {
      val w = WorldView.fromSnapshot(
        aa,
        trackIndirectlyConnected = true,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set(aa.address, bb.address))
      )

      val w1 = w.memberEvent(MemberJoined(joining), Set.empty)
      w1.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))

      val w2 = w.memberEvent(MemberWeaklyUp(weaklyUp), Set.empty)
      w2.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))

      val w3 = w.memberEvent(MemberUp(up), Set.empty)
      w3.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))

      val w4 = w.memberEvent(MemberLeft(leaving), Set.empty)
      w4.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))

      val w5 = w.memberEvent(MemberExited(exiting), Set.empty)
      w5.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))

      val w6 = w.memberEvent(MemberDowned(down), Set.empty)
      w6.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))
    }

    "move a member from reachable to unreachable" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address)),
        )

      // todo should selfMember really become unreachable
      val w1 = w.unreachableMember(aa)
      w1.reachableNodes.map(_.member) should ===(Set(bb))
      w1.unreachableNodes.map(_.member) should ===(Set(aa))

      val w2 = w.unreachableMember(bb)
      w2.reachableNodes.map(_.member) should ===(Set(aa))
      w2.unreachableNodes.map(_.member) should ===(Set(bb))
    }

    "move a member from unreachable to reachable" in {
      val w1 = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb), Set(bb), seenBy = Set(aa.address, bb.address)),
        )
        .reachableMember(bb)

      w1.reachableNodes.map(_.member) should ===(Set(aa, bb))
      w1.unreachableNodes.map(_.member) should ===(Set.empty)

      val w2 = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb), Set(aa), seenBy = Set(aa.address, bb.address)),
        )
        .reachableMember(aa)

      w2.reachableNodes.map(_.member) should ===(Set(aa, bb))
      w2.unreachableNodes.map(_.member) should ===(Set.empty)
    }

    "move a member from reachable to indirectly connected" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address)),
        )

      // todo should selfMember really become unreachable
      val w1 = w.indirectlyConnectedMember(aa)
      w1.reachableNodes.map(_.member) should ===(Set(bb))
      w1.indirectlyConnectedNodes.map(_.member) should ===(Set(aa))

      val w2 = w.indirectlyConnectedMember(bb)
      w2.reachableNodes.map(_.member) should ===(Set(aa))
      w2.indirectlyConnectedNodes.map(_.member) should ===(Set(bb))
    }

    "move a member from indirectly connected to reachable" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address)),
        )

      val w1 = w.indirectlyConnectedMember(aa).reachableMember(aa)
      w1.reachableNodes.map(_.member) should ===(Set(aa, bb))

      val w2 = w.indirectlyConnectedMember(bb).reachableMember(bb)
      w2.reachableNodes.map(_.member) should ===(Set(aa, bb))
    }

    "move a member from unreachable to indirectly connected" in {
      val w1 = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb), Set(bb), seenBy = Set(aa.address, bb.address)),
        )
        .indirectlyConnectedMember(bb)

      w1.reachableNodes.map(_.member) should ===(Set(aa))
      w1.indirectlyConnectedNodes.map(_.member) should ===(Set(bb))

      val w2 = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb), Set(aa), seenBy = Set(aa.address, bb.address)),
        )
        .indirectlyConnectedMember(aa)

      w2.reachableNodes.map(_.member) should ===(Set(bb))
      w2.indirectlyConnectedNodes.map(_.member) should ===(Set(aa))
    }

    "move a member from indirectly connected to unreachable" in {
      val w1 = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb), seenBy = Set(aa.address, bb.address)),
        )
        .indirectlyConnectedMember(bb)
        .unreachableMember(bb)

      w1.reachableNodes.map(_.member) should ===(Set(aa))
      w1.unreachableNodes.map(_.member) should ===(Set(bb))

      val w2 = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, bb), Set(aa), seenBy = Set(aa.address, bb.address)),
        )
        .indirectlyConnectedMember(aa)
        .unreachableMember(aa)

      w2.reachableNodes.map(_.member) should ===(Set(bb))
      w2.unreachableNodes.map(_.member) should ===(Set(aa))
    }

    "remove an unreachable member" in {
      val w = WorldView.fromSnapshot(
        aa,
        trackIndirectlyConnected = true,
        CurrentClusterState(SortedSet(aa, bb, removed), Set(removed), seenBy = Set(aa.address, bb.address))
      )

      val w1 = w.memberEvent(MemberRemoved(removed, Removed), Set.empty)
      w1.nodes.toSortedSet.map(_.member) should ===(Set(aa, bb))
      w1.removedMembers.keySet should ===(Set(removed))
    }

    "remove a reachable member" in {
      val w = WorldView.fromSnapshot(
        aa,
        trackIndirectlyConnected = true,
        CurrentClusterState(SortedSet(aa, removed, cc), Set(cc), seenBy = Set(aa.address, bb.address))
      )

      val w1 = w.memberEvent(MemberRemoved(removed, Removed), Set.empty)
      w1.nodes.toSortedSet.map(_.member) should ===(Set(aa, cc))
      w1.removedMembers.keySet should ===(Set(removed))
    }

    "remove an indirectly connected member" in {
      val w = WorldView
        .fromSnapshot(
          aa,
          trackIndirectlyConnected = true,
          CurrentClusterState(SortedSet(aa, removed, cc), Set(cc), seenBy = Set(aa.address, bb.address))
        )
        .indirectlyConnectedMember(removed)

      val w1 = w.memberEvent(MemberRemoved(removed, Removed), Set.empty)
      w1.nodes.toSortedSet.map(_.member) should ===(Set(aa, cc))
      w1.removedMembers.keySet should ===(Set(removed))
    }
  }
}
