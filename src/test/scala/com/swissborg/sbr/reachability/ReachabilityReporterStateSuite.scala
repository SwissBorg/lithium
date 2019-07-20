package com.swissborg.sbr
package reachability

import akka.actor.Address
import akka.cluster.MemberStatus.Up
import akka.cluster.UniqueAddress
import akka.cluster.swissborg.TestMember
import cats.data.State._
import cats.implicits._
import com.swissborg.sbr.reachability.ReachabilityReporterState.updatedReachability
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.SortedSet

class ReachabilityReporterStateSuite extends WordSpec with Matchers {
  val defaultDc = "dc-default"
  val sWithDefaultDc = ReachabilityReporterState(defaultDc)
  val aa = UniqueAddress(Address("akka.tcp", "sys", "a", 2552), 1L)
  val bb = UniqueAddress(Address("akka.tcp", "sys", "b", 2552), 2L)
  val cc = UniqueAddress(Address("akka.tcp", "sys", "c", 2552), 3L)
  val dd = UniqueAddress(Address("akka.tcp", "sys", "d", 2552), 4L)
  val ee = UniqueAddress(Address("akka.tcp", "sys", "e", 2552), 5L)

  "SBReachabilityReporterState" must {
    "only add members in other data-centers" in {
      sWithDefaultDc
        .add(TestMember.withUniqueAddress(aa, Up, Set.empty, defaultDc))
        .otherDcMembers should ===(SortedSet.empty[UniqueAddress])

      sWithDefaultDc
        .add(TestMember.withUniqueAddress(aa, Up, Set.empty, "dc-other"))
        .otherDcMembers should ===(SortedSet(aa))
    }

    "be reachable when empty" in {
      updatedReachability(aa).runA(sWithDefaultDc).value should ===(
        Some(ReachabilityStatus.Reachable)
      )

      updatedReachability(bb).runA(sWithDefaultDc).value should ===(
        Some(ReachabilityStatus.Reachable)
      )

      (updatedReachability(bb) >> updatedReachability(bb)).runA(sWithDefaultDc).value should ===(
        None
      )
    }

    "be unreachable when there is no suspicious detection" in {
      val s = sWithDefaultDc.withUnreachableFrom(aa, bb, 0)
      updatedReachability(bb).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))

      val s1 = sWithDefaultDc.withReachable(bb).withUnreachableFrom(aa, bb, 0)
      updatedReachability(bb).runA(s1).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "be indirectly connected when there is a suspicious detection" in {
      val s = sWithDefaultDc.withSuspiciousDetection(bb, cc, dd, 1)
      updatedReachability(dd).runA(s).value should ===(
        Some(ReachabilityStatus.IndirectlyConnected)
      )
    }

    "be unreachable when a suspicious detection is resolved" in {
      val s =
        sWithDefaultDc.withSuspiciousDetection(aa, cc, dd, 1).withUnreachableFrom(aa, dd, 0)
      updatedReachability(dd).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "be unreachable only when all suspicious detections are resolved" in {
      val s =
        sWithDefaultDc
          .withSuspiciousDetection(cc, aa, bb, 1)
          .withSuspiciousDetection(cc, dd, bb, 1)
          .withSuspiciousDetection(ee, aa, bb, 1)

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

    "ignore a suspicious detection for old versions" in {
      val s = sWithDefaultDc
        .withSuspiciousDetection(ee, aa, bb, 2)
        .withSuspiciousDetection(cc, aa, bb, 1)

      (for {
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(cc, bb, 1))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.IndirectlyConnected))
    }

    "reset a suspicious detection when there is a new version" in {
      val s = sWithDefaultDc
        .withSuspiciousDetection(cc, aa, bb, 1)
        .withSuspiciousDetection(ee, aa, bb, 2)

      (for {
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(ee, bb, 2))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "update a suspicious detection when it is for the current version" in {
      val s = sWithDefaultDc
        .withSuspiciousDetection(cc, aa, bb, 1)
        .withSuspiciousDetection(ee, aa, bb, 1)

      (for {
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(cc, bb, 1))
        _ <- modify[ReachabilityReporterState](_.withUnreachableFrom(ee, bb, 1))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "become reachable after calling reachable" in {
      val s =
        sWithDefaultDc
          .withUnreachableFrom(aa, bb, 0)
          .withSuspiciousDetection(aa, bb, cc, 1)
          .withSuspiciousDetection(dd, bb, cc, 1)

      (for {
        _ <- modify[ReachabilityReporterState](_.withReachable(bb))
        status <- updatedReachability(bb)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.Reachable))

      (for {
        _ <- modify[ReachabilityReporterState](_.withReachable(cc))
        status <- updatedReachability(cc)
      } yield status).runA(s).value should ===(Some(ReachabilityStatus.Reachable))
    }

    "update suspicious detections when a node is removed" in {
      val s =
        sWithDefaultDc
          .withSuspiciousDetection(aa, bb, cc, 1)
          .withSuspiciousDetection(aa, dd, bb, 2)
          .withSuspiciousDetection(dd, cc, aa, 1)

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

    "expect a suspicious detection ack" in {
      val ack = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, dd, 0L)

      val s = sWithDefaultDc.expectSuspiciousDetectionAck(ack)

      s.pendingSuspiciousDetectionAcks.get(bb) should ===(Some(Set(ack)))
    }

    "expect multiple suspicious detection acks" in {
      val ack0 = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, dd, 0L)
      val ack1 = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, ee, 0L)
      val ack2 = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, dd, 1L)

      val s = sWithDefaultDc
        .expectSuspiciousDetectionAck(ack0)
        .expectSuspiciousDetectionAck(ack1)
        .expectSuspiciousDetectionAck(ack2)

      s.pendingSuspiciousDetectionAcks.get(bb) should ===(
        Some(Set(ack0, ack1, ack2))
      )
    }

    "remove the suspicious detection ack" in {
      val ack0 = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, dd, 0L)
      val ack1 = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, ee, 0L)
      val ack2 = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, dd, 1L)

      val s = sWithDefaultDc
        .expectSuspiciousDetectionAck(ack0)
        .expectSuspiciousDetectionAck(ack1)
        .expectSuspiciousDetectionAck(ack2)
        .registerSuspiciousDetectionAck(ack1)

      s.pendingSuspiciousDetectionAcks.get(bb) should ===(Some(Set(ack0, ack2)))
    }

    "remove all suspicious detection acks" in {
      val ack0 = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, dd, 0L)
      val ack1 = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, ee, 0L)
      val ack2 = ReachabilityReporter.SuspiciousDetectionAck(bb, cc, dd, 1L)

      val s = sWithDefaultDc
        .expectSuspiciousDetectionAck(ack0)
        .expectSuspiciousDetectionAck(ack1)
        .expectSuspiciousDetectionAck(ack2)
        .remove(bb)

      s.pendingSuspiciousDetectionAcks.get(bb) should ===(None)
    }

    "become unreachable after removing all the suspicious detections" in {
      val s = sWithDefaultDc
        .withSuspiciousDetection(cc, dd, ee, 1)
        .withSuspiciousDetection(aa, dd, ee, 1)
        .withoutSuspiciousDetection(cc, dd, ee, 1)
        .withoutSuspiciousDetection(aa, dd, ee, 1)

      updatedReachability(ee).runA(s).value should ===(Some(ReachabilityStatus.Unreachable))
    }

    "stay indirectly-connected when removing part of the suspicious detections" in {
      val s = sWithDefaultDc
        .withSuspiciousDetection(cc, dd, ee, 1)
        .withSuspiciousDetection(aa, dd, ee, 1)
        .withoutSuspiciousDetection(aa, dd, ee, 1)

      updatedReachability(ee).runA(s).value should ===(
        Some(ReachabilityStatus.IndirectlyConnected)
      )
    }
  }
}
