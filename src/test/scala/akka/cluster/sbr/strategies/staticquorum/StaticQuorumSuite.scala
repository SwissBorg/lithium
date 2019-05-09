package akka.cluster.sbr.strategies.staticquorum

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus.Up
import akka.cluster.sbr.strategies.staticquorum.StaticQuorum.TooManyNodes
import akka.cluster.sbr.{DownReachable, DownUnreachable, Idle, WorldView}
import akka.cluster.sbr.utils.TestMember
import eu.timepit.refined.auto._
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.SortedSet

class StaticQuorumSuite extends WordSpec with Matchers {
  val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up, Set("role"))
  val dd = TestMember(Address("akka.tcp", "sys", "d", 2552), Up, Set("role"))
  val ee = TestMember(Address("akka.tcp", "sys", "e", 2552), Up, Set("role"))

  "StaticQuorum" must {
    "down the unreachable nodes when part of a quorum" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set.empty)
      )

      StaticQuorum("", 2).takeDecision(w) should ===(Right(DownUnreachable(w)))
    }

    "down the unreachable nodes when part of a quorum (with role)" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, dd), seenBy = Set.empty)
      )

      StaticQuorum("role", 2).takeDecision(w) should ===(Right(DownUnreachable(w)))
    }

    "down the reachable nodes when not part of a quorum" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty)
      )

      StaticQuorum("", 2).takeDecision(w) should ===(Right(DownReachable(w)))
    }

    "down the reachable nodes when not part of a quorum (with role)" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc, dd, ee), Set(aa, bb, dd, ee), seenBy = Set.empty)
      )

      StaticQuorum("role", 2).takeDecision(w) should ===(Right(DownReachable(w)))
    }

    "down the reachable nodes when there is an unreachable quorum" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty)
      )

      StaticQuorum("", 2).takeDecision(w) should ===(Right(DownReachable(w)))

      val w1 = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb, cc), seenBy = Set.empty)
      )

      StaticQuorum("", 2).takeDecision(w1) should ===(Right(DownReachable(w1)))
    }

    "do nothing when the reachable nodes form a quorum and there are no unreachable nodes" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
      )

      StaticQuorum("", 2).takeDecision(w) should ===(Right(Idle))
    }

    "do nothing when the reachable nodes form a quorum and there are no unreachable nodes (with role)" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), Set(aa, bb), seenBy = Set.empty)
      )

      StaticQuorum("role", 2).takeDecision(w) should ===(Right(Idle))
    }

    "down the reachable nodes do not form quorum and there are no unreachable nodes" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa), seenBy = Set.empty)
      )

      StaticQuorum("", 2).takeDecision(w) should ===(Right(DownReachable(w)))
    }

    "down the reachable nodes do not form quorum and there are no unreachable nodes (with role)" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(aa, bb), seenBy = Set.empty)
      )

      StaticQuorum("role", 2).takeDecision(w) should ===(Right(DownReachable(w)))
    }

    "fail when the quorum size is less than the majority of considered nodes" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), seenBy = Set.empty)
      )

      StaticQuorum("", 1).takeDecision(w) should ===(Left(TooManyNodes))

      val w1 = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc, dd), seenBy = Set.empty)
      )

      StaticQuorum("", 2).takeDecision(w1) should ===(Left(TooManyNodes))
    }
  }
}
