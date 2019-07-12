package com.swissborg.sbr
package splitbrain

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus._
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

  val joiningBB = TestMember(Address("akka.tcp", "sys", "b", 2552), Joining)
  val weaklyUpBB = TestMember(Address("akka.tcp", "sys", "b", 2552), WeaklyUp)
  val leavingBB = TestMember(Address("akka.tcp", "sys", "b", 2552), Leaving)
  val exitingBB = TestMember(Address("akka.tcp", "sys", "b", 2552), Exiting)
  val downBB = TestMember(Address("akka.tcp", "sys", "b", 2552), Down)
  val removedBB = TestMember(Address("akka.tcp", "sys", "b", 2552), Removed)

  val joining = TestMember(Address("akka.tcp", "sys", "joining", 2552), Joining)
  val weaklyUp = TestMember(Address("akka.tcp", "sys", "weaklyUp", 2552), WeaklyUp)

  "DiffInfo" must {
    "detect no change" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd))
      )

      val diff = DiffInfo(w, w)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalNonReachableNodes shouldBe false
    }

    "detect a new indirectly connected node" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd))
      )

      val updatedW = oldW.withIndirectlyConnectedNode(cc.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalNonReachableNodes shouldBe true
    }

    "detect a new unreachable node" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd))
      )

      val updatedW = oldW.withUnreachableNode(cc.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalNonReachableNodes shouldBe true
    }

    "detect a exiting member" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc))
      )

      val updatedW = oldW.addOrUpdate(exitingBB)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalNonReachableNodes shouldBe false
    }

    "detect a downed member" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc))
      )

      val updatedW = oldW.addOrUpdate(downBB)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalNonReachableNodes shouldBe false
    }

    "detect a leaving member" in {
      val oldW = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc))
      )

      val updatedW = oldW.addOrUpdate(leavingBB)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalNonReachableNodes shouldBe false
    }

    "ignore change from indirectly connected to unreachable" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc, dd))
        )
        .withIndirectlyConnectedNode(dd.uniqueAddress)

      val updatedW = oldW.withUnreachableNode(dd.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalNonReachableNodes shouldBe true
    }

    "ignore change from unreachable to indirectly connected" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc, dd))
        )
        .withUnreachableNode(dd.uniqueAddress)

      val updatedW = oldW.withIndirectlyConnectedNode(dd.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe false
      diff.hasAdditionalNonReachableNodes shouldBe true
    }

    "ignore reachable joining members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc))
        )

      val updatedW = oldW.addOrUpdate(joining)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalNonReachableNodes shouldBe false
    }

    "ignore indirectly connected joining members" in {
      val oldW = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc)))

      val updatedW = oldW.addOrUpdate(joining).withIndirectlyConnectedNode(joining.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalNonReachableNodes shouldBe false
    }

    "ignore unreachable joining members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc))
        )

      val updatedW = oldW.addOrUpdate(joining).withUnreachableNode(joining.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalNonReachableNodes shouldBe false
    }

    "ignore reachable weakly-up members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc))
        )

      val updatedW = oldW.addOrUpdate(weaklyUp).withReachableNode(weaklyUp.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalNonReachableNodes shouldBe false
    }

    "ignore indirectly connected weakly-up members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc))
        )

      val updatedW = oldW.addOrUpdate(weaklyUp).withIndirectlyConnectedNode(weaklyUp.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalNonReachableNodes shouldBe false
    }

    "ignore unreachable weakly-up members" in {
      val oldW = WorldView
        .fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc))
        )

      val updatedW = oldW.addOrUpdate(weaklyUp).withUnreachableNode(weaklyUp.uniqueAddress)

      val diff = DiffInfo(oldW, updatedW)

      diff.changeIsStable shouldBe true
      diff.hasAdditionalNonReachableNodes shouldBe false
    }
  }
}
