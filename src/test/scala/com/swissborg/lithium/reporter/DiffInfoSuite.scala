package com.swissborg.lithium

package reporter

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus._
import akka.cluster.swissborg.TestMember
import com.swissborg.lithium.WorldView
import com.swissborg.lithium.reporter.SplitBrainReporter.DiffInfo

import scala.collection.immutable.SortedSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DiffInfoSuite extends AnyWordSpec with Matchers {
  private val aa = TestMember(Address("akka", "sys", "a", 2552), Up)
  private val bb = TestMember(Address("akka", "sys", "b", 2552), Up)
  private val cc = TestMember(Address("akka", "sys", "c", 2552), Up)
  private val dd = TestMember(Address("akka", "sys", "d", 2552), Up)

  private val leavingBB = TestMember(Address("akka", "sys", "b", 2552), Leaving)
  private val exitingBB = TestMember(Address("akka", "sys", "b", 2552), Exiting)
  private val downBB    = TestMember(Address("akka", "sys", "b", 2552), Down)

  private val joining  = TestMember(Address("akka", "sys", "joining", 2552), Joining)
  private val weaklyUp = TestMember(Address("akka", "sys", "weaklyUp", 2552), WeaklyUp)

  "DiffInfo" must {
    "detect no change" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd)))

      val diff = DiffInfo(w, w)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalConsideredNonReachableNodes shouldBe false
    }

    "detect a new indirectly connected node" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd)))

      val updatedW = oldW.withIndirectlyConnectedNode(cc.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe true
    }

    "detect a new unreachable node" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd)))

      val updatedW = oldW.withUnreachableNode(cc.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe true
    }

    "detect a exiting member" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(exitingBB)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe false
    }

    "detect a downed member" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(downBB)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe false
    }

    "detect a leaving member" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(leavingBB)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe false
    }

    "ignore change from indirectly connected to unreachable" in {
      val oldW = WorldView
        .fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd)))
        .withIndirectlyConnectedNode(dd.uniqueAddress)

      val updatedW = oldW.withUnreachableNode(dd.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe false
    }

    "ignore change from unreachable to indirectly connected" in {
      val oldW =
        WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd))).withUnreachableNode(dd.uniqueAddress)

      val updatedW = oldW.withIndirectlyConnectedNode(dd.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe false
    }

    "ignore reachable joining members" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(joining)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalConsideredNonReachableNodes shouldBe false
    }

    "consider indirectly connected joining members" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(joining).withIndirectlyConnectedNode(joining.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe true
    }

    "consider unreachable joining members" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(joining).withUnreachableNode(joining.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe true
    }

    "ignore reachable weakly-up members" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(weaklyUp).withReachableNode(weaklyUp.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalConsideredNonReachableNodes shouldBe false
    }

    "consider indirectly connected weakly-up members" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(weaklyUp).withIndirectlyConnectedNode(weaklyUp.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe true
    }

    "consider unreachable weakly-up members" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(weaklyUp).withUnreachableNode(weaklyUp.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalConsideredNonReachableNodes shouldBe true
    }
  }
}
