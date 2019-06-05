package com.swissborg.sbr.splitbrain

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus.{Down, Exiting, Joining, Leaving, Removed, Up, WeaklyUp}
import akka.cluster.swissborg.TestMember
import com.swissborg.sbr.WorldView
import com.swissborg.sbr.splitbrain.SBSplitBrainReporter.DiffInfo
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.SortedSet

class DiffInfoSuite extends WordSpec with Matchers {
  val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up)
  val dd = TestMember(Address("akka.tcp", "sys", "d", 2552), Up)
  val ee = TestMember(Address("akka.tcp", "sys", "e", 2552), Up)

  val leavingBB = TestMember(Address("akka.tcp", "sys", "b", 2552), Leaving)
  val exitingBB = TestMember(Address("akka.tcp", "sys", "b", 2552), Exiting)
  val downBB    = TestMember(Address("akka.tcp", "sys", "b", 2552), Down)
  val removedBB = TestMember(Address("akka.tcp", "sys", "b", 2552), Removed)

  val joining  = TestMember(Address("akka.tcp", "sys", "joining", 2552), Joining)
  val weaklyUp = TestMember(Address("akka.tcp", "sys", "weaklyUp", 2552), WeaklyUp)
  val up       = TestMember(Address("akka.tcp", "sys", "up", 2552), Up)
  val leaving  = TestMember(Address("akka.tcp", "sys", "leaving", 2552), Leaving)
  val exiting  = TestMember(Address("akka.tcp", "sys", "exiting", 2552), Exiting)
  val down     = TestMember(Address("akka.tcp", "sys", "down", 2552), Down)
  val removed  = TestMember(Address("akka.tcp", "sys", "removed", 2552), Removed)

  "DiffInfo" must {
    "detect no change" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd), seenBy = Set.empty)
      )

      val diff = DiffInfo(w, w)

      diff.changeIsStable shouldBe true
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }

    "detect a new indirectly connected node" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd), seenBy = Set.empty)
      )

      val updatedW = oldW.withIndirectlyConnectedMember(cc)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe true
    }

    "detect a new unreachable node" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd), seenBy = Set.empty)
      )

      val updatedW = oldW.withUnreachableMember(cc)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe true
    }

    "detect a exiting member" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
      )

      val updatedW = oldW.updateMember(exitingBB, Set.empty)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }

    "detect a downed member" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
      )

      val updatedW = oldW.updateMember(downBB, Set.empty)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }

    "detect a leaving member" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
      )

      val updatedW = oldW.updateMember(leavingBB, Set.empty)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }

    "ignore change from indirectly connected to unreachable" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc, dd), seenBy = Set.empty)
        )
        .withIndirectlyConnectedMember(dd)

      val updatedW = oldW.withUnreachableMember(dd)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe true
    }

    "ignore change from unreachable to indirectly connected" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc, dd), seenBy = Set.empty)
        )
        .withUnreachableMember(dd)

      val updatedW = oldW.withIndirectlyConnectedMember(dd)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe true
    }

    "ignore reachable joining members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
        )

      val updatedW = oldW.withReachableMember(joining)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }

    "ignore indirectly connected joining members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
        )

      val updatedW = oldW.withIndirectlyConnectedMember(joining)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }

    "ignore unreachable joining members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
        )

      val updatedW = oldW.withUnreachableMember(joining)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }

    "ignore reachable weakly-up members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
        )

      val updatedW = oldW.withReachableMember(weaklyUp)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }

    "ignore indirectly connected weakly-up members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
        )

      val updatedW = oldW.withIndirectlyConnectedMember(weaklyUp)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }

    "ignore unreachable weakly-up members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
        )

      val updatedW = oldW.withUnreachableMember(weaklyUp)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasNewUnreachableOrIndirectlyConnected shouldBe false
    }
  }
}
