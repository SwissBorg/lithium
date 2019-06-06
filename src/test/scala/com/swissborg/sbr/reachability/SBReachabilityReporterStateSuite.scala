package com.swissborg.sbr.reachability

import akka.actor.{ActorPath, Address}
import akka.cluster.UniqueAddress
import com.swissborg.sbr.reachability.SBReachabilityReporter._
import com.swissborg.sbr.reachability.SBReachabilityReporterState.ContentionAggregator
import org.scalatest.{Matchers, WordSpec}

class SBReachabilityReporterStateSuite extends WordSpec with Matchers {
  import SBReachabilityReporterStateSuite._

  val aa = UniqueAddress(Address("akka.tcp", "sys", "a", 2552), 1L)
  val bb = UniqueAddress(Address("akka.tcp", "sys", "b", 2552), 2L)
  val cc = UniqueAddress(Address("akka.tcp", "sys", "c", 2552), 3L)
  val dd = UniqueAddress(Address("akka.tcp", "sys", "d", 2552), 4L)
  val ee = UniqueAddress(Address("akka.tcp", "sys", "e", 2552), 5L)

  "SBReachabilityReporterState" must {
    "be reachable when empty" in {
      val s = SBReachabilityReporterState(testPath(aa))
      s.updatedStatus(aa)._1 should ===(Some(Reachable))

      val (status, s2) = s.updatedStatus(bb)
      status should ===(Some(Reachable))

      s2.updatedStatus(bb)._1 should ===(None)
    }

    "be unreachable when there is no contention" in {
      val s = SBReachabilityReporterState(testPath(aa)).withUnreachableFrom(aa, bb)
      s.updatedStatus(bb)._1 should ===(Some(Unreachable))

      val s1 = SBReachabilityReporterState(testPath(aa)).withReachable(bb).withUnreachableFrom(aa, bb)
      s1.updatedStatus(bb)._1 should ===(Some(Unreachable))
    }

    "be indirectly connected when there is a contention" in {
      val s = SBReachabilityReporterState(testPath(aa)).withContention(bb, cc, dd, 1)
      s.updatedStatus(dd)._1 should ===(Some(IndirectlyConnected))
    }

    "be unreachable when a contention is resolved" in {
      val s = SBReachabilityReporterState(testPath(aa)).withContention(aa, cc, dd, 1).withUnreachableFrom(aa, dd)
      s.updatedStatus(dd)._1 should ===(Some(Unreachable))
    }

    "be unreachable only when all the contentions are resolved" in {
      val s =
        SBReachabilityReporterState(testPath(aa))
          .withContention(cc, aa, bb, 1)
          .withContention(cc, dd, bb, 1)
          .withContention(ee, aa, bb, 1)

      val (status, s1) = s.updatedStatus(bb)
      status should ===(Some(IndirectlyConnected))

      val s2            = s1.withUnreachableFrom(cc, bb)
      val (status2, s3) = s2.updatedStatus(bb)
      status2 should ===(None)

      val s4 = s3.withUnreachableFrom(ee, bb)
      s4.updatedStatus(bb)._1 should ===(Some(Unreachable))
    }

    "ignore a contention for old versions" in {
      val s = SBReachabilityReporterState(testPath(aa))
        .withContention(ee, aa, bb, 2)
        .withContention(cc, aa, bb, 1)

      val s1 = s.withUnreachableFrom(cc, bb)
      s1.updatedStatus(bb)._1 should ===(Some(IndirectlyConnected))
    }

    "reset a contention when there is a new version" in {
      val s = SBReachabilityReporterState(testPath(aa))
        .withContention(cc, aa, bb, 1)
        .withContention(ee, aa, bb, 2)

      val s1 = s.withUnreachableFrom(ee, bb)
      s1.updatedStatus(bb)._1 should ===(Some(Unreachable))
    }

    "update a contention when it is for the current version" in {
      val s = SBReachabilityReporterState(testPath(aa))
        .withContention(cc, aa, bb, 1)
        .withContention(ee, aa, bb, 1)

      val s1 = s.withUnreachableFrom(cc, bb).withUnreachableFrom(ee, bb)
      s1.updatedStatus(bb)._1 should ===(Some(Unreachable))
    }

    "become reachable after calling reachable" in {
      val s =
        SBReachabilityReporterState(testPath(aa))
          .withUnreachableFrom(aa, bb)
          .withContention(aa, bb, cc, 1)
          .withContention(dd, bb, cc, 1)

      val (status, s1) = s.withReachable(bb).updatedStatus(bb)
      status should ===(Some(Reachable))

      s1.withReachable(cc).updatedStatus(cc)._1 should ===(Some(Reachable))
    }

    "update contentions when a node is removed" in {
      val s =
        SBReachabilityReporterState(testPath(aa))
          .withContention(aa, bb, cc, 1)
          .withContention(aa, dd, bb, 2)
          .withContention(dd, cc, aa, 1)

      val s1 = s.remove(aa)
      s1.updatedStatus(cc)._1 should ===(Some(Unreachable))
      s1.updatedStatus(bb)._1 should ===(Some(Unreachable))

      val s2 = s.remove(bb)
      s2.updatedStatus(cc)._1 should ===(Some(Reachable))
      s2.updatedStatus(aa)._1 should ===(Some(IndirectlyConnected))
    }

    "not change contentions when merging the same contentions" in {
      val s0 =
        SBReachabilityReporterState(testPath(aa))
          .withContention(aa, bb, cc, 1)
          .withContention(aa, dd, bb, 2)
          .withContention(dd, cc, aa, 1)

      val s1 =
        SBReachabilityReporterState(testPath(bb))
          .withContention(aa, bb, cc, 1)
          .withContention(aa, dd, bb, 2)
          .withContention(dd, cc, aa, 1)

      s0.withContentions(s1.contentions).contentions should ===(s0.contentions)
    }

    "add new contentions when merging unknown ones" in {
      val s0 =
        SBReachabilityReporterState(testPath(aa))
          .withContention(dd, cc, aa, 1)

      val s1 =
        SBReachabilityReporterState(testPath(bb))
          .withContention(aa, bb, cc, 1)

      s0.withContentions(s1.contentions).contentions should ===(
        Map(cc -> Map(bb -> ContentionAggregator(Set(aa), 1)), aa -> Map(cc -> ContentionAggregator(Set(dd), 1)))
      )
    }

    "use the contention with the last version when merging contentions" in {
      val s0 =
        SBReachabilityReporterState(testPath(aa))
          .withContention(aa, bb, cc, 2) // newer
          .withContention(dd, cc, aa, 1)

      val s1 =
        SBReachabilityReporterState(testPath(bb))
          .withContention(aa, bb, cc, 1)
          .withContention(dd, cc, aa, 2) // newer

      s0.withContentions(s1.contentions).contentions should ===(
        Map(cc -> Map(bb -> ContentionAggregator(Set(aa), 2)), aa -> Map(cc -> ContentionAggregator(Set(dd), 2)))
      )
    }

    "merge the aggregators when merging contentions" in {
      val s0 =
        SBReachabilityReporterState(testPath(aa))
          .withContention(aa, cc, dd, 2) // newer

      val s1 =
        SBReachabilityReporterState(testPath(bb))
          .withContention(bb, cc, dd, 2)

      s0.withContentions(s1.contentions).contentions should ===(
        Map(dd -> Map(cc -> ContentionAggregator(Set(aa, bb), 2)))
      )
    }

    "expect a contention ack" in {
      val contentionAck = ContentionAck(bb, cc, dd, 0L)

      val s = SBReachabilityReporterState(testPath(aa)).expectContentionAck(contentionAck)

      s.pendingContentionAcks.get(bb) should ===(Some(Set(contentionAck)))
    }

    "expect multiple contention acks" in {
      val contentionAck0 = ContentionAck(bb, cc, dd, 0L)
      val contentionAck1 = ContentionAck(bb, cc, ee, 0L)
      val contentionAck2 = ContentionAck(bb, cc, dd, 1L)

      val s = SBReachabilityReporterState(testPath(aa))
        .expectContentionAck(contentionAck0)
        .expectContentionAck(contentionAck1)
        .expectContentionAck(contentionAck2)

      s.pendingContentionAcks.get(bb) should ===(Some(Set(contentionAck0, contentionAck1, contentionAck2)))
    }

    "remove the contention ack" in {
      val contentionAck0 = ContentionAck(bb, cc, dd, 0L)
      val contentionAck1 = ContentionAck(bb, cc, ee, 0L)
      val contentionAck2 = ContentionAck(bb, cc, dd, 1L)

      val s = SBReachabilityReporterState(testPath(aa))
        .expectContentionAck(contentionAck0)
        .expectContentionAck(contentionAck1)
        .expectContentionAck(contentionAck2)
        .registerContentionAck(contentionAck1)

      s.pendingContentionAcks.get(bb) should ===(Some(Set(contentionAck0, contentionAck2)))
    }

    "remove all contention acks" in {
      val contentionAck0 = ContentionAck(bb, cc, dd, 0L)
      val contentionAck1 = ContentionAck(bb, cc, ee, 0L)
      val contentionAck2 = ContentionAck(bb, cc, dd, 1L)

      val s = SBReachabilityReporterState(testPath(aa))
        .expectContentionAck(contentionAck0)
        .expectContentionAck(contentionAck1)
        .expectContentionAck(contentionAck2)
        .remove(bb)

      s.pendingContentionAcks.get(bb) should ===(None)
    }

    "expect an introduction ack" in {
      val introductionAck = IntroductionAck(bb)
      val s               = SBReachabilityReporterState(testPath(aa)).expectIntroductionAck(introductionAck)
      s.pendingIntroductionAcks.get(bb) should ===(Some(introductionAck))
    }

    "remove the pending introduction ack" in {
      val introductionAck = IntroductionAck(bb)

      val s = SBReachabilityReporterState(testPath(aa))
        .expectIntroductionAck(introductionAck)
        .registerIntroductionAck(introductionAck)

      s.pendingIntroductionAcks.get(bb) should ===(None)
    }

    "remove all pending introduction acks" in {
      val s = SBReachabilityReporterState(testPath(aa)).expectIntroductionAck(IntroductionAck(bb)).remove(bb)
      s.pendingIntroductionAcks.get(bb) should ===(None)
    }
  }
}

object SBReachabilityReporterStateSuite {
  def testPath(uniqueAddress: UniqueAddress): ActorPath =
    ActorPath.fromString(s"${uniqueAddress.address.toString}/user/test")
}
