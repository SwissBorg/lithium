package com.swissborg.sbr.strategies.keepmajority

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus.Up
import akka.cluster.swissborg.TestMember
import cats.implicits._
import com.swissborg.sbr.strategies.keepmajority.KeepMajority.Config
import com.swissborg.sbr.{DownReachable, DownUnreachable, Idle, WorldView}
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.SortedSet
import scala.util.Try

class KeepMajoritySuite extends WordSpec with Matchers {
  val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up, Set("role"))
  val dd = TestMember(Address("akka.tcp", "sys", "d", 2552), Up, Set("role"))
  val ee = TestMember(Address("akka.tcp", "sys", "e", 2552), Up, Set("role"))

  "KeepMajority" must {
    "down the unreachable nodes when part of a majority" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("")).takeDecision(w).get should ===(DownUnreachable(w))
    }

    "down the unreachable nodes when part of a majority (with role)" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, dd), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("role")).takeDecision(w).get should ===(DownUnreachable(w))
    }

    "down the reachable nodes when not part of a majority" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("")).takeDecision(w).get should ===(DownReachable(w))
    }

    "down the reachable nodes when not part of a majority (with role)" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, dd, ee), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("role")).takeDecision(w).get should ===(DownReachable(w))
    }

    "down the partition with the lowest address when there are an even number of nodes" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(cc, dd), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("")).takeDecision(w).get should ===(DownUnreachable(w))

      val w1 = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(aa, bb), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("")).takeDecision(w1).get should ===(DownReachable(w1))
    }

    "down the partition with the lowest address when there are an even number of nodes (with role)" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(dd), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("")).takeDecision(w).get should ===(DownUnreachable(w))

      val w1 = WorldView.fromSnapshot(
        dd,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(aa, bb, cc), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("")).takeDecision(w1).get should ===(DownReachable(w1))
    }

    "do nothing when the reachable nodes form a majority and there are no unreachable nodes" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("")).takeDecision(w).map(_.simplify).get should ===(Idle)
    }

    "down unreachable nodes when the reachable nodes form a majority and there are no unreachable nodes (with role)" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(aa, bb), seenBy = Set.empty)
      )

      new KeepMajority[Try](Config("role")).takeDecision(w).map(_.simplify).get should ===(DownUnreachable(w))
    }
  }
}
