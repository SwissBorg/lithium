package akka.cluster.sbr.strategies.keepreferee

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus.Up
import akka.cluster.sbr.utils.TestMember
import akka.cluster.sbr.{DownReachable, DownUnreachable, WorldView}
import eu.timepit.refined.auto._
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.SortedSet

class KeepRefereeSuite extends WordSpec with Matchers {
  private val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
  private val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
  private val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up)

  private val referee = aa.address.toString

  "KeepReferee" must {
    "down the unreachable nodes when being the referee node and reaching enough nodes" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb), seenBy = Set.empty)
      )

      KeepReferee(referee, 1).takeDecision(w) should ===(Right(DownUnreachable(w)))
    }

    "down the reachable nodes when being the referee and not reaching enough nodes" in {
      val w = WorldView.fromSnapshot(
        aa,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb), seenBy = Set.empty)
      )

      KeepReferee(referee, 3).takeDecision(w) should ===(Right(DownReachable(w)))
    }

    "down the unreachable nodes when the referee is reachable and reaching enough nodes" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb), seenBy = Set.empty)
      )

      KeepReferee(referee, 1).takeDecision(w) should ===(Right(DownUnreachable(w)))
    }

    "down the reachable nodes when the referee is reachable and not reaching enough nodes" in {
      val w = WorldView.fromSnapshot(
        cc,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(bb), seenBy = Set.empty)
      )

      KeepReferee(referee, 3).takeDecision(w) should ===(Right(DownReachable(w)))
    }

    "down the reachable nodes when the referee is unreachable" in {
      val w = WorldView.fromSnapshot(
        bb,
        CurrentClusterState(SortedSet(aa, bb, cc), Set(aa), seenBy = Set.empty)
      )

      KeepReferee(referee, 1).takeDecision(w) should ===(Right(DownReachable(w)))
      KeepReferee(referee, 3).takeDecision(w) should ===(Right(DownReachable(w)))
    }
  }
}
