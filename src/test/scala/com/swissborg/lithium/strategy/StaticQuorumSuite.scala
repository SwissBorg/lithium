package com.swissborg.lithium

package strategy

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus.{Joining, Up}
import akka.cluster.swissborg.TestMember
import cats.effect.SyncIO
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable.SortedSet
import org.scalatest.matchers.should.Matchers

class StaticQuorumSuite extends AnyWordSpecLike with Matchers {
  val aa = TestMember(Address("akka", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka", "sys", "c", 2552), Up, Set("role"))
  val dd = TestMember(Address("akka", "sys", "d", 2552), Up, Set("role"))
  val ee = TestMember(Address("akka", "sys", "e", 2552), Up, Set("role"))

  val joiningCC = TestMember(Address("akka", "sys", "c", 2552), Joining, Set("role"))

  "StaticQuorum" must {
    "down the unreachable nodes when part of a quorum" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set.empty))

      new StaticQuorum[SyncIO](StaticQuorumConfig("", 2)).takeDecision(w).unsafeRunSync() should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the unreachable nodes when part of a quorum (with role)" in {
      val w =
        WorldView.fromSnapshot(cc,
                               CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, dd), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("role", 2))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the reachable nodes when not part of a quorum" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("", 2))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownReachable(w)
      )
    }

    "down the reachable nodes when not part of a quorum (with role)" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, dd, ee), seenBy = Set.empty)
      )

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("role", 2))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownReachable(w)
      )
    }

    "down the reachable nodes when there is an unreachable quorum" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("", 2))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownReachable(w)
      )
    }

    "down the unreachable nodes when the reachable nodes form a quorum and there are no unreachable nodes" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("", 2))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the unreachable nodes when the reachable nodes form a quorum and there are only joining unreachable nodes" in {
      val w =
        WorldView.fromSnapshot(aa,
                               CurrentClusterState(SortedSet(aa, bb, joiningCC), Set(joiningCC), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("", 2))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the unreachable when the reachable nodes form a quorum and there are no unreachable nodes (with role)" in {
      val w =
        WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(aa, bb), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("role", 2))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the reachable nodes do not form quorum and there are no unreachable nodes" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("", 2))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownReachable(w)
      )
    }

    "down the reachable nodes do not form quorum and there are no unreachable nodes (with role)" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), Set(aa, bb), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("role", 2))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownReachable(w)
      )
    }

    "down the cluster when the quorum size is less than the majority of considered nodes" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("", 1))
        .takeDecision(w)
        .unsafeRunSync() should ===(
        Decision.DownReachable(w)
      )

      val w1 = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc, dd), seenBy = Set.empty))

      new strategy.StaticQuorum[SyncIO](StaticQuorumConfig("", 2))
        .takeDecision(w1)
        .unsafeRunSync() should ===(
        Decision.DownReachable(w1)
      )
    }
  }
}
