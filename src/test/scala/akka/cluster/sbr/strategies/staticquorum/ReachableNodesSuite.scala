package akka.cluster.sbr.strategies.staticquorum

import akka.actor.Address
import akka.cluster.MemberStatus.Up
import akka.cluster.sbr.utils.TestMember
import org.scalatest.{Matchers, WordSpec}

class ReachableNodesSuite extends WordSpec with Matchers {
  val aa = TestMember(Address("akka.tcp", "sys", "a", 2552), Up)
  val bb = TestMember(Address("akka.tcp", "sys", "b", 2552), Up)
  val cc = TestMember(Address("akka.tcp", "sys", "c", 2552), Up)
  val dd = TestMember(Address("akka.tcp", "sys", "d", 2552), Up)
  val ee = TestMember(Address("akka.tcp", "sys", "e", 2552), Up)

  "ReachableNodes" in {
    ""
  }
}
