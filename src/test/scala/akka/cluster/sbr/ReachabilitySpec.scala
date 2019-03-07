package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import org.scalacheck.Prop.{forAll, BooleanOperators}
import org.scalacheck.Properties

class ReachabilitySpec extends Properties("ReachableNodeGroup") {
  property("weakly-up") = forAll { (reachability: Reachability, memberWeaklyUp: MemberWeaklyUp) =>
    reachability.memberEvent(memberWeaklyUp) == reachability
  }

  property("member-removed") = forAll { (reachability: Reachability, memberRemoved: MemberRemoved) =>
    (reachability.reachableNodes.contains(memberRemoved.member) || reachability.unreachableNodes.contains(
      memberRemoved.member
    )) ==> {
      val reachability0 = reachability.memberEvent(memberRemoved)

      !reachability0.reachableNodes.contains(memberRemoved.member) && !reachability0.unreachableNodes.contains(
        memberRemoved.member
      )
    }
  }

  property("become reachable 1") = forAll { (reachability: Reachability, memberEvent: MemberEvent) =>
    (memberEvent match {
      case _: MemberWeaklyUp | _: MemberRemoved => false
      case _                                    => true
    }) ==> {
      val reachability0 = reachability.memberEvent(memberEvent)

      reachability0.reachableNodes.contains(memberEvent.member) && !reachability0.unreachableNodes.contains(
        memberEvent.member
      )
    }
  }

  property("become reachable 2") = forAll { (reachability: Reachability, reachableMember: ReachableMember) =>
    val reachability0 = reachability.reachabilityEvent(reachableMember)

    reachability0.reachableNodes.contains(reachableMember.member) && !reachability0.unreachableNodes.contains(
      reachableMember.member
    )
  }

  property("become unreachable") = forAll { (reachability: Reachability, unreachableMember: UnreachableMember) =>
    val reachability0 = reachability.reachabilityEvent(unreachableMember)

    !reachability0.reachableNodes.contains(unreachableMember.member) && reachability0.unreachableNodes.contains(
      unreachableMember.member
    )
  }

  property("memberEvent creates no overlaps") = forAll {
    (reachability: Reachability, memberEvents: List[MemberEvent]) =>
      (reachability.reachableNodes.intersect(reachability.unreachableNodes) == Set.empty) ==> {
        val reachability0 = memberEvents.foldLeft(reachability) {
          case (reachability, memberEvent) => reachability.memberEvent(memberEvent)
        }

        reachability0.reachableNodes.intersect(reachability0.unreachableNodes) == Set.empty
      }
  }

  property("reachabilityEvent creates no overlaps") = forAll {
    (reachability: Reachability, reachabilityEvents: List[ReachabilityEvent]) =>
      (reachability.reachableNodes.intersect(reachability.unreachableNodes) == Set.empty) ==> {
        val reachability0 = reachabilityEvents.foldLeft(reachability) {
          case (reachability, reachabilityEvent) => reachability.reachabilityEvent(reachabilityEvent)
        }

        reachability0.reachableNodes.intersect(reachability0.unreachableNodes) == Set.empty
      }
  }
}
