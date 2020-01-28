package com.swissborg.lithium

package strategy

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus.{Joining, Up, WeaklyUp}
import akka.cluster.swissborg.TestMember
import cats.implicits._
import com.swissborg.lithium.strategy.Decision._

import scala.collection.immutable.SortedSet
import scala.util.Try
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class KeepMajoritySuite extends AnyWordSpec with Matchers {
  val aa = TestMember(Address("akka", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka", "sys", "c", 2552), Up, Set("role"))
  val dd = TestMember(Address("akka", "sys", "d", 2552), Up, Set("role"))
  val ee = TestMember(Address("akka", "sys", "e", 2552), Up, Set("role"))
  val ff = TestMember(Address("akka", "sys", "f", 2552), Joining)
  val gg = TestMember(Address("akka", "sys", "g", 2552), WeaklyUp)
  val hh = TestMember(Address("akka", "sys", "h", 2552), Joining)
  val ii = TestMember(Address("akka", "sys", "i", 2552), WeaklyUp)

  "KeepMajority" must {
    "down the unreachable nodes when part of a majority" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set.empty))

      new KeepMajority[Try](KeepMajority.Config(""), false).takeDecision(w).get should ===(DownUnreachable(w))
    }

    "down the unreachable nodes when part of a majority (with role)" in {
      val w =
        WorldView.fromSnapshot(cc,
                               CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, dd), seenBy = Set.empty))

      new strategy.KeepMajority[Try](KeepMajority.Config("role"), false).takeDecision(w).get should ===(
        DownUnreachable(w)
      )
    }

    "down the reachable nodes when not part of a majority" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty))

      new strategy.KeepMajority[Try](KeepMajority.Config(""), false).takeDecision(w).get should ===(DownReachable(w))
    }

    "down the reachable nodes when not part of a majority (with role)" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, dd, ee), seenBy = Set.empty)
      )

      new strategy.KeepMajority[Try](KeepMajority.Config("role"), false).takeDecision(w).get should ===(
        DownReachable(w)
      )
    }

    "down the partition with the lowest address when there are an even number of nodes" in {
      val w =
        WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(cc, dd), seenBy = Set.empty))

      new strategy.KeepMajority[Try](KeepMajority.Config(""), false).takeDecision(w).get should ===(DownUnreachable(w))

      val w1 =
        WorldView.fromSnapshot(cc, CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(aa, bb), seenBy = Set.empty))

      new strategy.KeepMajority[Try](KeepMajority.Config(""), false).takeDecision(w1).get should ===(DownReachable(w1))
    }

    "down the partition with the lowest address when there are an even number of nodes (with role)" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd), seenBy = Set.empty))

      new strategy.KeepMajority[Try](KeepMajority.Config(""), false).takeDecision(w).get should ===(DownUnreachable(w))

      val w1 =
        WorldView.fromSnapshot(dd, CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(aa, bb, cc), seenBy = Set.empty))

      new strategy.KeepMajority[Try](KeepMajority.Config(""), false).takeDecision(w1).get should ===(DownReachable(w1))
    }

    "do nothing when the reachable nodes form a majority and there are no unreachable nodes" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty))

      new strategy.KeepMajority[Try](KeepMajority.Config(""), false).takeDecision(w).map(_.simplify).get should ===(
        Idle
      )
    }

    "down unreachable nodes when the reachable nodes form a majority and there are no unreachable nodes (with role)" in {
      val w =
        WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(aa, bb), seenBy = Set.empty))

      new strategy.KeepMajority[Try](KeepMajority.Config("role"), false).takeDecision(w).map(_.simplify).get should ===(
        DownUnreachable(w)
      )
    }

    "down reachable nodes when joining nodes become up on the other side and becomes a majority (no weaklyUpMembersAllowed)" in {
      val w =
        WorldView.fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc, dd, ee, ff, hh), Set(dd, ee, ff, hh), seenBy = Set.empty)
        )

      new strategy.KeepMajority[Try](KeepMajority.Config(""), false).takeDecision(w).map(_.simplify).get should ===(
        DownReachable(w)
      )
    }

    "not down reachable nodes when joining nodes become up on the other side and becomes a majority (no weaklyUpMembersAllowed)" in {
      val w =
        WorldView.fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc, dd, ee, gg, ii), Set(dd, ee, gg, ii), seenBy = Set.empty)
        )

      new strategy.KeepMajority[Try](KeepMajority.Config(""), false).takeDecision(w).map(_.simplify).get should ===(
        DownUnreachable(w)
      )
    }

    "down reachable nodes when weakly-up nodes become up on the other side and becomes a majority (weaklyUpMembersAllowed)" in {
      val w =
        WorldView.fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc, dd, ee, gg, ii), Set(dd, ee, gg, ii), seenBy = Set.empty)
        )

      new strategy.KeepMajority[Try](KeepMajority.Config(""), true).takeDecision(w).map(_.simplify).get should ===(
        DownReachable(w)
      )
    }

    "not down reachable nodes when joining nodes become weakly-up on the other side and becomes a majority (weaklyUpMembersAllowed)" in {
      val w =
        WorldView.fromSnapshot(
          aa,
          CurrentClusterState(SortedSet(aa, bb, cc, dd, ee, ff, hh), Set(dd, ee, ff, hh), seenBy = Set.empty)
        )

      new strategy.KeepMajority[Try](KeepMajority.Config(""), true).takeDecision(w).map(_.simplify).get should ===(
        DownUnreachable(w)
      )
    }
  }
}
