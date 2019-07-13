package com.swissborg.sbr
package reachability

import akka.actor.Address
import akka.cluster.UniqueAddress
import cats.data.State._
import cats.implicits._
import com.swissborg.sbr.reachability.ReachabilityReporterState.updatedReachability
import org.scalatest.{Matchers, WordSpec}

class ReachabilityReporterStateSuite extends WordSpec with Matchers {
  val aa = UniqueAddress(Address("akka.tcp", "sys", "a", 2552), 1L)
  val bb = UniqueAddress(Address("akka.tcp", "sys", "b", 2552), 2L)
  val cc = UniqueAddress(Address("akka.tcp", "sys", "c", 2552), 3L)
  val dd = UniqueAddress(Address("akka.tcp", "sys", "d", 2552), 4L)
  val ee = UniqueAddress(Address("akka.tcp", "sys", "e", 2552), 5L)

  "SBReachabilityReporterState" must {
    "be reachable when empty" in {
      val s = ReachabilityReporterState(aa)
      updatedReachability(aa).runA(s).value should ===(Some(ReachabilityStatus.Reachable))

      updatedReachability(bb).runA(s).value should ===(Some(ReachabilityStatus.Reachable))

      (updatedReachability(bb) >> updatedReachability(bb)).runA(s).value should ===(None)
    }

    "be unreachable when there is no contention" in {
      val s = ReachabilityReporterState(aa).withUnreachableFrom(aa, bb, 0)
      updatedReachability(bb).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))

      val s1 = ReachabilityReporterState(aa).withReachable(bb).withUnreachableFrom(aa, bb, 0)
      updatedReachability(bb).runA(s1).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "be indirectly connected when there is a contention" in {
      val s = ReachabilityReporterState(aa).withContention(bb, cc, dd, 1)
      updatedReachability(dd).runA(s).value should ===(
        Some(ReachabilityStatus.IndirectlyConnected)
      )
    }

    "be unreachable when a contention is resolved" in {
      val s =
        ReachabilityReporterState(aa).withContention(aa, cc, dd, 1).withUnreachableFrom(aa, dd, 0)
      updatedReachability(dd).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "be unreachable only when all the contentions are resolved" in {
      val s =
        ReachabilityReporterState(aa)
          .withContention(cc, aa, bb, 1)
          .withContention(cc, dd, bb, 1)
          .withContention(ee, aa, bb, 1)

      updatedReachability(bb).runA(s).value should ===(
        Some(ReachabilityStatus.IndirectlyConnected)
      )

      (for {
        _ <- updatedReachability(bb)
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(cc, bb, 0))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(None)

      (for {
        _ <- updatedReachability(bb)
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(cc, bb, 0))
        _ <- updatedReachability(bb)
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(ee, bb, 0))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "ignore a contention for old versions" in {
      val s = ReachabilityReporterState(aa)
        .withContention(ee, aa, bb, 2)
        .withContention(cc, aa, bb, 1)

      (for {
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(cc, bb, 1))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.IndirectlyConnected))
    }

    "reset a contention when there is a new version" in {
      val s = ReachabilityReporterState(aa)
        .withContention(cc, aa, bb, 1)
        .withContention(ee, aa, bb, 2)

      (for {
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(ee, bb, 2))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "update a contention when it is for the current version" in {
      val s = ReachabilityReporterState(aa)
        .withContention(cc, aa, bb, 1)
        .withContention(ee, aa, bb, 1)

      (for {
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(cc, bb, 1))
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(ee, bb, 1))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "become reachable after calling reachable" in {
      val s =
        ReachabilityReporterState(aa)
          .withUnreachableFrom(aa, bb, 0)
          .withContention(aa, bb, cc, 1)
          .withContention(dd, bb, cc, 1)

      (for {
        _ <- modify[ReachabilityReporterState](_.withReachable(bb))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.Reachable))

      (for {
        _ <- modify[ReachabilityReporterState](_.withReachable(cc))
        status <- updatedReachability(cc)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.Reachable))
    }

    "update contentions when a node is removed" in {
      val s =
        ReachabilityReporterState(aa)
          .withContention(aa, bb, cc, 1)
          .withContention(aa, dd, bb, 2)
          .withContention(dd, cc, aa, 1)

      val removeAA = modify[ReachabilityReporterState](_.remove(aa))

      (removeAA >> updatedReachability(cc)).runA(s).value should ===(
        Some(ReachabilityStatus.Unreachable)
      )
      (removeAA >> updatedReachability(bb)).runA(s).value should ===(
        Some(ReachabilityStatus.Unreachable)
      )

      val removeBB = modify[ReachabilityReporterState](_.remove(bb))

      (removeBB >> updatedReachability(cc)).runA(s).value should ===(
        Some(ReachabilityStatus.Reachable)
      )
      (removeBB >> updatedReachability(aa)).runA(s).value should ===(
        Some(ReachabilityStatus.IndirectlyConnected)
      )
    }

    "expect a contention ack" in {
      val contentionAck = ReachabilityReporter.ContentionAck(bb, cc, dd, 0L)

      val s = ReachabilityReporterState(aa).expectContentionAck(contentionAck)

      s.pendingContentionAcks.get(bb) should ===(Some(Set(contentionAck)))
    }

    "expect multiple contention acks" in {
      val contentionAck0 = ReachabilityReporter.ContentionAck(bb, cc, dd, 0L)
      val contentionAck1 = ReachabilityReporter.ContentionAck(bb, cc, ee, 0L)
      val contentionAck2 = ReachabilityReporter.ContentionAck(bb, cc, dd, 1L)

      val s = ReachabilityReporterState(aa)
        .expectContentionAck(contentionAck0)
        .expectContentionAck(contentionAck1)
        .expectContentionAck(contentionAck2)

      s.pendingContentionAcks.get(bb) should ===(
        Some(Set(contentionAck0, contentionAck1, contentionAck2))
      )
    }

    "remove the contention ack" in {
      val contentionAck0 = ReachabilityReporter.ContentionAck(bb, cc, dd, 0L)
      val contentionAck1 = ReachabilityReporter.ContentionAck(bb, cc, ee, 0L)
      val contentionAck2 = ReachabilityReporter.ContentionAck(bb, cc, dd, 1L)

      val s = ReachabilityReporterState(aa)
        .expectContentionAck(contentionAck0)
        .expectContentionAck(contentionAck1)
        .expectContentionAck(contentionAck2)
        .registerContentionAck(contentionAck1)

      s.pendingContentionAcks.get(bb) should ===(Some(Set(contentionAck0, contentionAck2)))
    }

    "remove all contention acks" in {
      val contentionAck0 = ReachabilityReporter.ContentionAck(bb, cc, dd, 0L)
      val contentionAck1 = ReachabilityReporter.ContentionAck(bb, cc, ee, 0L)
      val contentionAck2 = ReachabilityReporter.ContentionAck(bb, cc, dd, 1L)

      val s = ReachabilityReporterState(aa)
        .expectContentionAck(contentionAck0)
        .expectContentionAck(contentionAck1)
        .expectContentionAck(contentionAck2)
        .remove(bb)

      s.pendingContentionAcks.get(bb) should ===(None)
    }

    "become unreachable after removing all the contentions" in {
      val s = ReachabilityReporterState(aa)
        .withContention(cc, dd, ee, 1)
        .withContention(aa, dd, ee, 1)
        .withoutContention(cc, dd, ee, 1)
        .withoutContention(aa, dd, ee, 1)

      updatedReachability(ee).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "stay indirectly-connected when removing part of the contentions" in {
      val s = ReachabilityReporterState(aa)
        .withContention(cc, dd, ee, 1)
        .withContention(aa, dd, ee, 1)
        .withoutContention(aa, dd, ee, 1)

      updatedReachability(ee).runA(s).value should ===(
        Some(ReachabilityStatus.IndirectlyConnected)
      )
    }
  }
}
