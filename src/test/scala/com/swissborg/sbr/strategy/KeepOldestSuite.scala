package com.swissborg.sbr
package strategy

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus._
import akka.cluster.swissborg.TestMember
import cats.implicits._
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.SortedSet
import scala.util.Try

class KeepOldestSuite extends WordSpec with Matchers {
  val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up, Set("role"))
  val dd = TestMember(Address("akka.tcp", "sys", "d", 2552), Up, Set("role"))
  val ee = TestMember(Address("akka.tcp", "sys", "e", 2552), Up, Set("role"))

  val weaklyUpBB = TestMember(Address("akka.tcp", "sys", "b", 2552), WeaklyUp)

  "KeepOldest" must {
    "down the unreachable nodes when being the oldest node and not alone" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb), seenBy = Set.empty)
      )

      new KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the other partition when being the oldest and alone" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down itself when being the oldest node and alone" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownReachable(w)
      )
    }

    "down the reachable nodes when the oldest is unreachable and not alone" in {
      val w = WorldView.fromSnapshot(
        bb,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(aa, cc), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownReachable(w)
      )
      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownReachable(w)
      )
    }

    "down the correct nodes when the oldest is unreachable and alone" in {
      val w = WorldView.fromSnapshot(
        bb,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(aa), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownReachable(w)
      )
      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the unreachable nodes when the oldest is reachable and not alone" in {
      val w = WorldView.fromSnapshot(
        bb,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
    }

    // ---

    "down the unreachable nodes when being the oldest node and not alone (with role)" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(bb), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the other partition when being the oldest and alone (with role)" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(bb, dd, ee), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down itself when being the oldest node and alone (with role)" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, dd, ee), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownReachable(w)
      )
    }

    "down the reachable nodes when the oldest is unreachable and not alone (with role)" in {
      val w = WorldView.fromSnapshot(
        dd,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(cc, dd), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownReachable(w)
      )
      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownReachable(w)
      )
    }

    "down the correct nodes when the oldest is unreachable and alone (with role)" in {
      val w = WorldView.fromSnapshot(
        dd,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(cc, aa, bb), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownReachable(w)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the unreachable nodes when the oldest is reachable and not alone (with role)" in {
      val w = WorldView.fromSnapshot(
        dd,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, ee), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = "role"))
        .takeDecision(w)
        .get should ===(
        Decision.DownUnreachable(w)
      )
    }

    "not down the oldest node when alone in the cluster" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = false, role = ""))
        .takeDecision(w)
        .map(_.simplify)
        .get should ===(Decision.Idle)
    }

    "down the oldest node when alone in the cluster" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = ""))
        .takeDecision(w)
        .map(_.simplify)
        .get should ===(Decision.DownReachable(w))
    }

    "down the cluster when uncertain if alone" in {
      // 3 nodes cluster that split into three partitions

      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(aa, bb), seenBy = Set.empty)
      )

      val keepOldest =
        new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = ""))

      keepOldest.takeDecision(w).get should ===(Decision.DownReachable(w))

      val w1 = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty)
      )

      keepOldest.takeDecision(w1).get should ===(Decision.DownReachable(w1))
    }

    "take in account joining nodes when checking if the unreachable oldest node is alone" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, weaklyUpBB, cc), Set(aa, weaklyUpBB), seenBy = Set.empty)
      )

      new strategy.KeepOldest[Try](KeepOldest.Config(downIfAlone = true, role = ""))
        .takeDecision(w)
        .get should ===(
        Decision.DownReachable(w)
      )
    }
  }
}
