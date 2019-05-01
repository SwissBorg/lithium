//package akka.cluster.sbr
//
//import akka.actor.Address
//import akka.cluster.ClusterEvent._
//import akka.cluster.MemberStatus._
//import akka.cluster.sbr.utils._
//import org.scalatest.{Matchers, WordSpec}
//
//import scala.collection.immutable.SortedSet
//
//class WorldViewSuite extends WordSpec with Matchers {
//  val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
//  val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
//  val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up)
//  val dd = TestMember(Address("akka.tcp", "sys", "d", 2552), Up)
//  val ee = TestMember(Address("akka.tcp", "sys", "e", 2552), Up)
//
//  val joining  = TestMember(Address("akka.tcp", "sys", "d", 2552), Joining)
//  val weaklyUp = TestMember(Address("akka.tcp", "sys", "d", 2552), WeaklyUp)
//  val up       = TestMember(Address("akka.tcp", "sys", "d", 2552), Up)
//  val leaving  = TestMember(Address("akka.tcp", "sys", "d", 2552), Leaving)
//  val exiting  = TestMember(Address("akka.tcp", "sys", "d", 2552), Exiting)
//  val down     = TestMember(Address("akka.tcp", "sys", "d", 2552), Down)
//  val removed  = TestMember(Address("akka.tcp", "sys", "d", 2552), Removed)
//
//  "SBRFailureDetectorState" must {
//    "construct from a snapshot" in {
//      val w = WorldView
//        .fromSnapshot(
//          aa,
//          trackIndirectlyConnected = true,
//          CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set(aa.address, bb.address)),
//        )
//        .indirectlyConnected(bb)
//
////      w.seenBy should ===(Set(aa.address, bb.address))
//      w.reachableNodes.map(_.member) should ===(Set(aa))
//      w.indirectlyConnectedNodes.map(_.member) should ===(Set(bb))
//      w.unreachableNodes.map(_.member) should ===(Set(cc))
//    }
//
//    "add a member as reachable when getting the first member event" in {
//      val w = WorldView.fromSnapshot(
//        aa,
//        trackIndirectlyConnected = true,
//        Set.empty,
//        CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set(aa.address, bb.address))
//      )
//
//      val w1 = w.memberEvent(MemberJoined(joining))
//      w1.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))
//
//      val w2 = w.memberEvent(MemberWeaklyUp(weaklyUp))
//      w2.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))
//
//      val w3 = w.memberEvent(MemberUp(up))
//      w3.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))
//
//      val w4 = w.memberEvent(MemberLeft(leaving))
//      w4.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))
//
//      val w5 = w.memberEvent(MemberExited(exiting))
//      w5.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))
//
//      val w6 = w.memberEvent(MemberDowned(down))
//      w6.reachableNodes.map(_.member) should ===(Set(aa, bb, dd))
//    }
//
//    "not add a removed member" in {
//      val w = WorldView.fromSnapshot(
//        aa,
//        trackIndirectlyConnected = true,
//        Set.empty,
//        CurrentClusterState(SortedSet(aa, bb, cc), Set(cc), seenBy = Set(aa.address, bb.address))
//      )
//
//      val w1 = w.memberEvent(MemberRemoved(removed, Removed))
//      w1.reachableNodes.map(_.member) should ===(Set(aa, bb))
//    }
//
//    "remove an unreachable member" in {
//      val w = WorldView.fromSnapshot(
//        aa,
//        trackIndirectlyConnected = true,
//        Set.empty,
//        CurrentClusterState(SortedSet(aa, bb, removed), Set(removed), seenBy = Set(aa.address, bb.address))
//      )
//
//      val w1 = w.memberEvent(MemberRemoved(removed, Removed))
//      w1.nodes.toSortedSet.map(_.member) should ===(Set(aa, bb))
//    }
//
//    "remove a reachable member" in {
//      val w = WorldView.fromSnapshot(
//        aa,
//        trackIndirectlyConnected = true,
//        Set.empty,
//        CurrentClusterState(SortedSet(aa, removed, cc), Set(cc), seenBy = Set(aa.address, bb.address))
//      )
//
//      val w1 = w.memberEvent(MemberRemoved(removed, Removed))
//      w1.nodes.toSortedSet.map(_.member) should ===(Set(aa, cc))
//    }
//  }
//}
