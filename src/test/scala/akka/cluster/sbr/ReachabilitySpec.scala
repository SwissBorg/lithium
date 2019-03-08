package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.Member
import org.scalacheck.Prop._
import org.scalacheck.{Prop, Properties}
import akka.cluster.sbr.ArbitraryInstances._

class ReachabilitySpec extends Properties("Reachability") {
  import ReachabilitySpec._

  property("reachability") = forAll { reachability: Reachability =>
    noIntersection(reachability)
  }

  property("memberEvent") = forAll { (reachability: Reachability, memberEvent: MemberEvent) =>
    memberEvent match {
      case _: MemberWeaklyUp => reachability.memberEvent(memberEvent) == reachability

      case _: MemberRemoved =>
        val reachability0 = reachability.memberEvent(memberEvent)
        !memberReachable(reachability0, memberEvent.member) && !memberUnreachable(reachability0, memberEvent.member)

      case _ =>
        val reachability0 = reachability.memberEvent(memberEvent)
        memberReachable(reachability0, memberEvent.member) && !memberUnreachable(reachability0, memberEvent.member)
    }
  }

  property("reachabilityEvent") = forAll { (reachability: Reachability, reachabilityEvent: ReachabilityEvent) =>
    reachabilityEvent match {
      case UnreachableMember(member) =>
        val reachability0 = reachability.reachabilityEvent(reachabilityEvent)
        !memberReachable(reachability0, member) && memberUnreachable(reachability0, member)

      case ReachableMember(member) =>
        val reachability0 = reachability.reachabilityEvent(reachabilityEvent)
        memberReachable(reachability0, member) && !memberUnreachable(reachability0, member)
    }
  }

  property("noIntersection") = forAll {
    (reachability: Reachability, memberEvents: List[MemberEvent], reachabilityEvents: List[ReachabilityEvent]) =>
      val reachability0 = memberEvents.foldLeft(reachability) {
        case (reachability, memberEvent) => reachability.memberEvent(memberEvent)
      }

      val reachability1 = reachabilityEvents.foldLeft(reachability0) {
        case (reachability, reachabilityEvent) => reachability.reachabilityEvent(reachabilityEvent)
      }

      noIntersection(reachability1)
  }

  property("reachableNodes") = forAll { reachability: Reachability =>
    reachability.reachableNodes.map(_.node).subsetOf(reachability.m.keySet) :| "not a subset" &&
    reachability.reachableNodes
      .map(_.node)
      .forall(n => reachability.m.get(n).forall(_ == Reachable)) :| "not all are reachable"
  }

  property("unreachableNodes") = forAll { reachability: Reachability =>
    reachability.unreachableNodes.map(_.node).subsetOf(reachability.m.keySet) :| "not a subset" &&
    reachability.unreachableNodes
      .map(_.node)
      .forall(n => reachability.m.get(n).forall(_ == Unreachable)) :| "not all are unreachable"
  }
}

object ReachabilitySpec {
  def classifyPreExistentMember(reachability: Reachability, member: Member)(prop: Prop): Prop = {
    def preExistentMember(member: Member): Boolean =
      memberReachable(reachability, member) || memberUnreachable(reachability, member)

    classify(preExistentMember(member), "pre-existent", "non-existent")(prop)
  }

  def noIntersection(reachability: Reachability): Boolean =
    reachability.reachableNodes.map(_.node).intersect(reachability.unreachableNodes.map(_.node)).isEmpty

  def memberReachable(reachability: Reachability, member: Member): Boolean =
    reachability.reachableNodes.contains(ReachableNode(member))

  def memberUnreachable(reachability: Reachability, member: Member): Boolean =
    reachability.unreachableNodes.contains(UnreachableNode(member))
}
