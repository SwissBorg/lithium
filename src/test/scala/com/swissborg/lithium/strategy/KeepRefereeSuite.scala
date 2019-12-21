package com.swissborg.lithium

package strategy

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus.Up
import akka.cluster.swissborg.TestMember
import cats.Id
import eu.timepit.refined._
import eu.timepit.refined.auto._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable.SortedSet
import org.scalatest.matchers.should.Matchers

class KeepRefereeSuite extends AnyWordSpecLike with Matchers {
  private val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
  private val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
  private val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up)

  private val referee =
    refineV[SBAddress](aa.address.toString).left.map(new IllegalArgumentException(_)).toTry.get

  "KeepReferee" must {
    "down the unreachable nodes when being the referee node and reaching enough nodes" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), Set(bb), seenBy = Set.empty))

      new KeepReferee[Id](KeepReferee.Config(referee, 1)).takeDecision(w) should ===(Decision.DownUnreachable(w))
    }

    "down the reachable nodes when being the referee and not reaching enough nodes" in {
      val w = WorldView.fromSnapshot(aa, CurrentClusterState(SortedSet(aa, bb, cc), Set(bb), seenBy = Set.empty))

      new strategy.KeepReferee[Id](KeepReferee.Config(referee, 3)).takeDecision(w) should ===(Decision.DownReachable(w))
    }

    "down the unreachable nodes when the referee is reachable and reaching enough nodes" in {
      val w = WorldView.fromSnapshot(cc, CurrentClusterState(SortedSet(aa, bb, cc), Set(bb), seenBy = Set.empty))

      new strategy.KeepReferee[Id](KeepReferee.Config(referee, 1)).takeDecision(w) should ===(
        Decision.DownUnreachable(w)
      )
    }

    "down the reachable nodes when the referee is reachable and not reaching enough nodes" in {
      val w = WorldView.fromSnapshot(cc, CurrentClusterState(SortedSet(aa, bb, cc), Set(bb), seenBy = Set.empty))

      new strategy.KeepReferee[Id](KeepReferee.Config(referee, 3)).takeDecision(w) should ===(Decision.DownReachable(w))
    }

    "down the reachable nodes when the referee is unreachable" in {
      val w = WorldView.fromSnapshot(bb, CurrentClusterState(SortedSet(aa, bb, cc), Set(aa), seenBy = Set.empty))

      new strategy.KeepReferee[Id](KeepReferee.Config(referee, 1)).takeDecision(w) should ===(Decision.DownReachable(w))

      new strategy.KeepReferee[Id](KeepReferee.Config(referee, 3)).takeDecision(w) should ===(Decision.DownReachable(w))
    }

    "compile for valid addresses" in {
      """refineMV[SBAddress]("protocol://system@address:1234")""" should compile
      """refineMV[SBAddress]("a.b.c://system@address:1234")""" should compile
      """refineMV[SBAddress]("a.b.c://system@127.0.0.1:1234")""" should compile
      """refineMV[SBAddress]("a.b.c://system@d.e.f:1234")""" should compile
    }
  }
}
