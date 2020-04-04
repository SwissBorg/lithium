package com.swissborg.lithium

package reachability

import akka.actor.Address
import akka.cluster.MemberStatus.{Down, Up}
import akka.cluster.UniqueAddress
import akka.cluster.swissborg.{LithiumReachability, TestMember}
import com.swissborg.lithium.reporter.SplitBrainReporter.{NodeIndirectlyConnected, NodeReachable, NodeUnreachable}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ReachabilityReporterStateSuite extends AnyWordSpec with Matchers {
  private val defaultDc      = "dc-default"
  private val sWithDefaultDc = ReachabilityReporterState(defaultDc)
  private val aa             = UniqueAddress(Address("akka", "sys", "a", 2552), 1L)
  private val bb             = UniqueAddress(Address("akka", "sys", "b", 2552), 2L)
  private val cc             = UniqueAddress(Address("akka", "sys", "c", 2552), 3L)
  private val dd             = UniqueAddress(Address("akka.tcp", "sys", "d", 2552), 4L)

  "ReachabilityReporterState" must {
    "ignore members from the same DC" in {
      sWithDefaultDc
        .withMembers(Set(TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)))
        .otherDcMembers should ===(
        Set.empty
      )
    }

    "add members from another DC" in {
      sWithDefaultDc
        .withMembers(Set(TestMember.withUniqueAddress(aa, Up, Set.empty, "dc-other")))
        .otherDcMembers should ===(Set(aa))
    }

    "remove the member" in {
      val s = sWithDefaultDc
        .withMembers(
          Set(
            TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc),
            TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc),
            TestMember.withUniqueAddress(cc, Up, Set.empty, defaultDc)
          )
        )
        .copy(latestIndirectlyConnectedNodes = Set(aa), latestUnreachableNodes = Set(bb))
        .withMembers(Set.empty)

      s.latestUnreachableNodes should ===(Set.empty)
      s.latestIndirectlyConnectedNodes should ===(Set.empty)
      s.otherDcMembers should ===(Set.empty)
    }

    "initialize from a DC" in {
      sWithDefaultDc.otherDcMembers should ===(Set.empty)
      sWithDefaultDc.latestIndirectlyConnectedNodes should ===(Set.empty)
      sWithDefaultDc.latestUnreachableNodes should ===(Set.empty)
      sWithDefaultDc.selfDataCenter should ===(defaultDc)
      sWithDefaultDc.selfDataCenter should ===(defaultDc)
    }

    "yield the correct indirectly-connected and unreachable nodes" in {
      val aaMember = TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)
      val bbMember = TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc)
      val ccMember = TestMember.withUniqueAddress(cc, Up, Set.empty, defaultDc)

      val getEvents = for {
        _ <- ReachabilityReporterState.withSeenBy(Set(aa.address, cc.address))
        events <- ReachabilityReporterState.withReachability(
          LithiumReachability(Set(aa), Map(bb -> Set(aa), cc -> Set(aa)))
        )
      } yield events

      val (s, events) = getEvents
        .run(
          sWithDefaultDc
            .withMembers(Set(aaMember, bbMember, ccMember))
            .withSeenBy(Set(aa.address, cc.address))
        )
        .value

      s.latestIndirectlyConnectedNodes should ===(Set(aa, cc))
      s.latestUnreachableNodes should ===(Set(bb))
      s.latestReachableNodes should ===(Set.empty)
      events.toSet should ===(Set(NodeIndirectlyConnected(aa), NodeIndirectlyConnected(cc), NodeUnreachable(bb)))
    }

    "ignore observations made by downed nodes" in {
      val aaMember = TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)
      val bbMember = TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc)
      val ccMember = TestMember.withUniqueAddress(cc, Down, Set.empty, defaultDc)

      val getEvents = for {
        _ <- ReachabilityReporterState.withSeenBy(Set(aa.address, cc.address))
        events <- ReachabilityReporterState.withReachability(
          LithiumReachability(Set(aa, cc), Map(bb -> Set(cc)))
        )
      } yield events

      val (s, events) = getEvents
        .run(
          sWithDefaultDc
            .withMembers(Set(aaMember, bbMember, ccMember))
            .withSeenBy(Set(aa.address, cc.address))
        )
        .value

      s.latestIndirectlyConnectedNodes should ===(Set.empty)
      s.latestUnreachableNodes should ===(Set.empty)
      s.latestReachableNodes should ===(Set(aa, bb, cc))
      events.toSet should ===(Set(NodeReachable(aa), NodeReachable(bb), NodeReachable(cc)))
    }

    "ignore nodes of other data-centers" in {
      val aaMember = TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)
      val bbMember = TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc)
      val ccMember = TestMember.withUniqueAddress(cc, Up, Set.empty, "dc-other")
      val ddMember = TestMember.withUniqueAddress(dd, Up, Set.empty, defaultDc)

      val getEvents = for {
        _ <- ReachabilityReporterState.withSeenBy(Set(aa.address, cc.address, dd.address))
        events <- ReachabilityReporterState.withReachability(
          LithiumReachability(Set(cc, dd), Map(bb -> Set(cc), aa -> Set(bb), cc -> Set(dd)))
        )
      } yield events

      val (s, events) = getEvents
        .run(
          sWithDefaultDc
            .withMembers(Set(aaMember, bbMember, ccMember, ddMember))
            .withSeenBy(Set(aa.address, cc.address, dd.address))
        )
        .value

      s.latestIndirectlyConnectedNodes should ===(Set(aa, bb))
      s.latestUnreachableNodes should ===(Set.empty)
      s.latestReachableNodes should ===(Set(dd))
      events.toSet should ===(Set(NodeIndirectlyConnected(aa), NodeIndirectlyConnected(bb), NodeReachable(dd)))
    }

    "do nothing when receiving a reachability changed followed by a seen-by changed" in {
      val aaMember = TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc)
      val bbMember = TestMember.withUniqueAddress(bb, Up, Set.empty, defaultDc)
      val ccMember = TestMember.withUniqueAddress(cc, Up, Set.empty, defaultDc)

      val getEvents = for {
        _      <- ReachabilityReporterState.withReachability(LithiumReachability(Set(aa), Map(bb -> Set(aa), cc -> Set(aa))))
        events <- ReachabilityReporterState.withSeenBy(Set(aa.address, cc.address))
      } yield events

      val events = getEvents
        .runA(
          sWithDefaultDc
            .withMembers(Set(aaMember, bbMember, ccMember))
            .withSeenBy(Set(aa.address, cc.address))
        )
        .value

      events shouldBe empty
    }

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
