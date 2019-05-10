package akka.cluster.sbr

import akka.actor.{ActorPath, Address}
import akka.cluster.UniqueAddress
import akka.cluster.sbr.SBFailureDetector._
import org.scalatest.{Matchers, WordSpec}

class SBFailureDetectorStateSuite extends WordSpec with Matchers {
  import SBFailureDetectorStateSuite._

  val aa = UniqueAddress(Address("akka.tcp", "sys", "a", 2552), 1L)
  val bb = UniqueAddress(Address("akka.tcp", "sys", "b", 2552), 2L)
  val cc = UniqueAddress(Address("akka.tcp", "sys", "c", 2552), 3L)
  val dd = UniqueAddress(Address("akka.tcp", "sys", "d", 2552), 4L)
  val ee = UniqueAddress(Address("akka.tcp", "sys", "e", 2552), 5L)

  "SBRFailureDetectorState" must {
    "be reachable when empty" in {
      val s = SBFailureDetectorState(testPath(aa))
      s.updatedStatus(aa)._1 should ===(Some(Reachable))

      val (status, s2) = s.updatedStatus(bb)
      status should ===(Some(Reachable))

      s2.updatedStatus(bb)._1 should ===(None)
    }

    "be unreachable when there is no contention" in {
      val s = SBFailureDetectorState(testPath(aa)).withUnreachableFrom(aa, bb)
      s.updatedStatus(bb)._1 should ===(Some(Unreachable))

      val s1 = SBFailureDetectorState(testPath(aa)).withReachable(bb).withUnreachableFrom(aa, bb)
      s1.updatedStatus(bb)._1 should ===(Some(Unreachable))
    }

    "be indirectly connected when there is a contention" in {
      val s = SBFailureDetectorState(testPath(aa)).withContention(bb, cc, dd, 1)
      s.updatedStatus(dd)._1 should ===(Some(IndirectlyConnected))
    }

    "be unreachable when a contention is resolved" in {
      val s = SBFailureDetectorState(testPath(aa)).withContention(aa, cc, dd, 1).withUnreachableFrom(aa, dd)
      s.updatedStatus(dd)._1 should ===(Some(Unreachable))
    }

    "be unreachable only when all the contentions are resolved" in {
      val s =
        SBFailureDetectorState(testPath(aa))
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
      val s = SBFailureDetectorState(testPath(aa))
        .withContention(ee, aa, bb, 2)
        .withContention(cc, aa, bb, 1)

      val s1 = s.withUnreachableFrom(cc, bb)
      s1.updatedStatus(bb)._1 should ===(Some(IndirectlyConnected))
    }

    "reset a contention when there is a new version" in {
      val s = SBFailureDetectorState(testPath(aa))
        .withContention(cc, aa, bb, 1)
        .withContention(ee, aa, bb, 2)

      val s1 = s.withUnreachableFrom(ee, bb)
      s1.updatedStatus(bb)._1 should ===(Some(Unreachable))
    }

    "update a contention when it is for the current version" in {
      val s = SBFailureDetectorState(testPath(aa))
        .withContention(cc, aa, bb, 1)
        .withContention(ee, aa, bb, 1)

      val s1 = s.withUnreachableFrom(cc, bb).withUnreachableFrom(ee, bb)
      s1.updatedStatus(bb)._1 should ===(Some(Unreachable))
    }

    "become reachable after calling reachable" in {
      val s =
        SBFailureDetectorState(testPath(aa))
          .withUnreachableFrom(aa, bb)
          .withContention(aa, bb, cc, 1)
          .withContention(dd, bb, cc, 1)

      val (status, s1) = s.withReachable(bb).updatedStatus(bb)
      status should ===(Some(Reachable))

      s1.withReachable(cc).updatedStatus(cc)._1 should ===(Some(Reachable))
    }

    "update contentions when a node is removed" in {
      val s =
        SBFailureDetectorState(testPath(aa))
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
  }
}

object SBFailureDetectorStateSuite {
  def testPath(uniqueAddress: UniqueAddress): ActorPath =
    ActorPath.fromString(s"${uniqueAddress.address.toString}/user/test")
}
