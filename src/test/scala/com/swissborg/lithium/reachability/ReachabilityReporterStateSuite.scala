package com.swissborg.lithium

package reachability

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus.Up
import akka.cluster.UniqueAddress
import akka.cluster.swissborg.{LithiumReachability, TestMember}
import cats.implicits._
import com.swissborg.lithium.reporter.SplitBrainReporter.{NodeIndirectlyConnected, NodeUnreachable}

import scala.collection.immutable.SortedSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ReachabilityReporterStateSuite extends AnyWordSpec with Matchers {
  private val defaultDc = "dc-default"
  private val aa        = UniqueAddress(Address("akka", "sys", "a", 2552), 1L)
  private val bb        = UniqueAddress(Address("akka", "sys", "b", 2552), 2L)
  private val cc        = UniqueAddress(Address("akka", "sys", "c", 2552), 3L)

  "ReachabilityReporterState" must {
    //    "ignore members from the same DC" in {
    //      sWithDefaultDc
    //        .withMember(TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc))
    //        .otherDcMembers should ===(
    //        Set.empty
    //      )
    //    }
    //
    //    "add members from another same DC" in {
    //      sWithDefaultDc
    //        .withMember(TestMember.withUniqueAddress(aa, Up, Set.empty, "dc-other"))
    //        .otherDcMembers should ===(Set(aa))
    //    }
    //
    //    "remove the member" in {
    //      val s = sWithDefaultDc
    //        .withMember(TestMember.withUniqueAddress(cc, Up, Set.empty, "dc-other"))
    //        .copy(latestIndirectlyConnectedNodes = Set(aa), latestUnreachableNodes = Set(bb))
    //        .remove(aa)
    //        .remove(bb)
    //        .remove(cc)
    //
    //      s.latestUnreachableNodes should ===(Set.empty)
    //      s.latestIndirectlyConnectedNodes should ===(Set.empty)
    //      s.otherDcMembers should ===(Set.empty)
    //    }
    //
    //    "initialize from a DC" in {
    //      val s = ReachabilityReporterState(defaultDc)
    //
    //      s.otherDcMembers should ===(Set.empty)
    //      s.latestIndirectlyConnectedNodes should ===(Set.empty)
    //      s.latestUnreachableNodes should ===(Set.empty)
    //      s.selfDataCenter should ===(defaultDc)
    //      s.selfDataCenter should ===(defaultDc)
    //    }
    //
    //    "initialize from a snapshot" in {
    //      val s = ReachabilityReporterState.fromSnapshot(
    //        CurrentClusterState(
    //          SortedSet(
    //            TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc),
    //            TestMember.withUniqueAddress(bb, Up, Set.empty, "dc-other"),
    //            TestMember.withUniqueAddress(cc, Up, Set.empty, "dc-other-2")
    //          )
    //        ),
    //        defaultDc
    //      )
    //
    //      s.otherDcMembers should ===(Set(bb, cc))
    //      s.latestIndirectlyConnectedNodes should ===(Set.empty)
    //      s.latestUnreachableNodes should ===(Set.empty)
    //      s.selfDataCenter should ===(defaultDc)
    //    }
    //  }
    //
    //  "yield the correct indirectly-connected and unreachable nodes" in {
    //    val aaMember = TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)
    //    val bbMember = TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc)
    //    val ccMember = TestMember.withUniqueAddress(cc, Up, Set.empty, defaultDc)
    //
    //    val getEvents = for {
    //      _ <- ReachabilityReporterState.withSeenBy(Set(aa.address, cc.address))
    //      events <- ReachabilityReporterState.withReachability(
    //                 LithiumReachability(Set(aa), Map(bb -> Set(aa), cc -> Set(aa)))
    //               )
    //    } yield events
    //
    //    val (s, events) = getEvents
    //      .run(
    //        ReachabilityReporterState.fromSnapshot(
    //          CurrentClusterState(members = SortedSet(aaMember, bbMember, ccMember), seenBy = Set(aa.address, cc.address)),
    //          defaultDc
    //        )
    //      )
    //      .value
    //
    //    s.latestIndirectlyConnectedNodes should ===(Set(aa, cc))
    //    s.latestUnreachableNodes should ===(Set(bb))
    //    s.latestReachableNodes should ===(Set.empty)
    //    events.toSet should ===(Set(NodeIndirectlyConnected(aa), NodeIndirectlyConnected(cc), NodeUnreachable(bb)))
    //  }
    //
    //  "ignore nodes of other data-centers" in {
    //    val aaMember = TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)
    //    val bbMember = TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc)
    //    val ccMember = TestMember.withUniqueAddress(cc, Up, Set.empty, "dc-other")
    //    val ddMember = TestMember.withUniqueAddress(dd, Up, Set.empty, defaultDc)
    //
    //    val getEvents = for {
    //      _ <- ReachabilityReporterState.withSeenBy(Set(aa.address, cc.address, dd.address))
    //      events <- ReachabilityReporterState.withReachability(
    //                 LithiumReachability(Set(cc, dd), Map(bb -> Set(cc), aa -> Set(bb), cc -> Set(dd)))
    //               )
    //    } yield events
    //
    //    val (s, events) = getEvents
    //      .run(
    //        ReachabilityReporterState.fromSnapshot(
    //          CurrentClusterState(
    //            members = SortedSet(aaMember, bbMember, ccMember, ddMember),
    //            seenBy = Set(aa.address, cc.address, dd.address)
    //          ),
    //          defaultDc
    //        )
    //      )
    //      .value
    //
    //    s.latestIndirectlyConnectedNodes should ===(Set(aa, bb))
    //    s.latestUnreachableNodes should ===(Set.empty)
    //    s.latestReachableNodes should ===(Set(dd))
    //    events.toSet should ===(Set(NodeIndirectlyConnected(aa), NodeIndirectlyConnected(bb), NodeReachable(dd)))
    //  }
    //
    //  "do nothing when receiving a reachability changed followed by a seen-by changed" in {
    //    val aaMember = TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)
    //    val bbMember = TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc)
    //    val ccMember = TestMember.withUniqueAddress(cc, Up, Set.empty, defaultDc)
    //
    //    val getEvents = for {
    //      _      <- ReachabilityReporterState.withReachability(LithiumReachability(Set(aa), Map(bb -> Set(aa), cc -> Set(aa))))
    //      events <- ReachabilityReporterState.withSeenBy(Set(aa.address, cc.address))
    //    } yield events
    //
    //    val events = getEvents
    //      .runA(
    //        ReachabilityReporterState.fromSnapshot(
    //          CurrentClusterState(members = SortedSet(aaMember, bbMember, ccMember), seenBy = Set(aa.address, cc.address)),
    //          defaultDc
    //        )
    //      )
    //      .value
    //
    //    events shouldBe empty
    //  }

    "re-evaluate the reachabilities when receiving two seen-by changes in a row" in {
      val aaMember = TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)
      val bbMember = TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc)
      val ccMember = TestMember.withUniqueAddress(cc, Up, Set.empty, defaultDc)

      val getEvents = for {
        _      <- ReachabilityReporterState.withReachability(LithiumReachability(Set(aa), Map(bb -> Set(aa), cc -> Set(aa))))
        _      <- ReachabilityReporterState.withSeenBy(Set(aa.address))
        events <- ReachabilityReporterState.withSeenBy(Set(aa.address, cc.address))
      } yield events

      val events = getEvents
        .runA(ReachabilityReporterState(aaMember.dataCenter).withMembers(Set(aaMember, bbMember, ccMember)))
        .value

      events.toSet should ===(Set(NodeIndirectlyConnected(aa), NodeIndirectlyConnected(cc), NodeUnreachable(bb)))
    }

    "re-evaluate the reachabilities when receiving two reachability changes in a row" in {
      val aaMember = TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)
      val bbMember = TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc)
      val ccMember = TestMember.withUniqueAddress(cc, Up, Set.empty, defaultDc)

      val getEvents = for {
        _ <- ReachabilityReporterState.withSeenBy(Set(aa.address, cc.address))
        _ <- ReachabilityReporterState.withReachability(LithiumReachability(Set(aa), Map(cc -> Set(aa))))
        events <- ReachabilityReporterState.withReachability(
          LithiumReachability(Set(aa), Map(bb -> Set(aa), cc -> Set(aa)))
        )
      } yield events

      val events = getEvents
        .runA(ReachabilityReporterState(aaMember.dataCenter).withMembers(Set(aaMember, bbMember, ccMember)))
        .value

      events.toSet should ===(Set(NodeUnreachable(bb)))
    }
  }
}
