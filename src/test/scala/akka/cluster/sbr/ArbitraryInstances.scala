package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.sbr.implicits._
import akka.cluster.{Member, MemberStatus, UniqueAddress, Reachability => _}
import cats.data.{NonEmptyMap, NonEmptySet}
import cats.kernel.Order
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import shapeless.tag
import shapeless.tag.@@

import scala.collection.immutable.{SortedMap, SortedSet}

object ArbitraryInstances extends ArbitraryInstances

trait ArbitraryInstances extends ArbitraryInstances0 {
  sealed trait JoiningTag
  type JoiningMember = Member @@ JoiningTag

  sealed trait WeaklyUpTag
  type WeaklyUpMember = Member @@ WeaklyUpTag

  sealed trait UpTag
  type UpMember = Member @@ UpTag

  sealed trait LeavingTag
  type LeavingMember = Member @@ LeavingTag

  sealed trait ExitingTag
  type ExitingMember = Member @@ ExitingTag

  sealed trait DownTag
  type DownMember = Member @@ DownTag

  sealed trait RemovedTag
  type RemovedMember = Member @@ RemovedTag

  sealed trait HealthyTag
  type HealthyWorldView = WorldView @@ HealthyTag

  sealed trait UpNumberConsistentTag
  type UpNumberConsistentWorldView = WorldView @@ UpNumberConsistentTag

  implicit val arbJoiningMember: Arbitrary[JoiningMember] = Arbitrary {
    val randJoiningMember = for {
      uniqueAddress <- arbitrary[UniqueAddress]
    } yield tag[JoiningTag][Member](Member(uniqueAddress, Set("dc-datacenter")))

    oneOf(
      randJoiningMember,
      const(tag[JoiningTag][Member](Member(UniqueAddress(Address("proto", "sys"), 0L), Set("dc-datacenter"))))
    )
  }

  implicit val arbWeaklyUpMember: Arbitrary[WeaklyUpMember] = Arbitrary(
    arbJoiningMember.arbitrary.map(m => tag[WeaklyUpTag][Member](m.copy(WeaklyUp)))
  )

  implicit val arbUpMember: Arbitrary[UpMember] = Arbitrary(
    arbJoiningMember.arbitrary.map(m => tag[UpTag][Member](m.copyUp(m.hashCode())))
  )

  implicit val arbLeavingMember: Arbitrary[LeavingMember] = Arbitrary(
    arbJoiningMember.arbitrary.map(m => tag[LeavingTag][Member](m.copy(Leaving)))
  )

  implicit val arbDownMember: Arbitrary[DownMember] = Arbitrary(
    arbJoiningMember.arbitrary.map(m => tag[DownTag][Member](m.copy(Down)))
  )

  implicit val arbRemovedMember: Arbitrary[RemovedMember] = Arbitrary(
    arbJoiningMember.arbitrary.map(m => tag[RemovedTag][Member](m.copy(Removed)))
  )

  implicit val arbExitingMember: Arbitrary[ExitingMember] = Arbitrary(
    arbLeavingMember.arbitrary.map(m => tag[ExitingTag][Member](m.copy(Exiting)))
  )

  implicit val arbMember: Arbitrary[Member] = Arbitrary(
    oneOf(
      arbWeaklyUpMember.arbitrary,
      arbUpMember.arbitrary,
      arbLeavingMember.arbitrary,
      arbDownMember.arbitrary,
      arbRemovedMember.arbitrary,
      arbExitingMember.arbitrary
    )
  )

  implicit val arbWorldView: Arbitrary[WorldView] = Arbitrary(
    arbNonEmptyMap[Member, Status].arbitrary.map(nodes => WorldView(nodes.head._1, nodes.head._2, nodes.tail))
  )

  implicit val arbHealthyWorldView: Arbitrary[HealthyWorldView] = Arbitrary(
    for {
      nodes <- arbNonEmptySet[Member].arbitrary
      worldView = WorldView(nodes.head, Reachable, otherStatuses = SortedMap(nodes.tail.map(_ -> Reachable).toSeq: _*))
    } yield tag[HealthyTag][WorldView](worldView)
  )

  implicit val arbUpNumberConsistentWorldView: Arbitrary[UpNumberConsistentWorldView] = Arbitrary(
    for {
      nodes <- arbNonEmptySet[WeaklyUpMember](taggedOrder[Member, WeaklyUpTag], arbWeaklyUpMember).arbitrary

      nodeStatuses = nodes.toNonEmptyList.zipWithIndex.map {
        case (weaklyUpMember, ix) => weaklyUpMember.copyUp(ix) -> Reachable
      }

      worldView = WorldView(nodeStatuses.head._1, nodeStatuses.head._2, SortedMap(nodeStatuses.toList: _*))
    } yield tag[UpNumberConsistentTag][WorldView](worldView)
  )

  implicit val arbReachability: Arbitrary[Status] =
    Arbitrary(oneOf(Reachable, Unreachable))

  implicit val arbReachableNode: Arbitrary[ReachableNode] =
    Arbitrary(arbMember.arbitrary.map(ReachableNode(_)))

  implicit val arbReachableConsideredNode: Arbitrary[ReachableConsideredNode] =
    Arbitrary(arbMember.arbitrary.map(ReachableConsideredNode(_)))

  implicit val arbUnreachableNode: Arbitrary[UnreachableNode] =
    Arbitrary(arbMember.arbitrary.map(UnreachableNode(_)))

  implicit val arbIndirectlyConnectedNode: Arbitrary[IndirectlyConnectedNode] =
    Arbitrary(arbMember.arbitrary.map(IndirectlyConnectedNode(_)))

  implicit val arbUniqueAddress: Arbitrary[UniqueAddress] =
    Arbitrary(for {
      address <- arbitrary[Address]
      longUid <- arbitrary[Long]
    } yield UniqueAddress(address, longUid))

  implicit val arbAddress: Arbitrary[Address] =
    Arbitrary(for {
      protocol <- alphaNumStr
      system   <- alphaNumStr
    } yield Address(protocol, system, None, None))

  implicit val arbMemberStatusFromJoining: Arbitrary[MemberStatus] =
    Arbitrary(oneOf[MemberStatus](WeaklyUp, WeaklyUp))

  implicit val arbMemberJoined: Arbitrary[MemberJoined] = Arbitrary(
    arbJoiningMember.arbitrary.map(MemberJoined)
  )

  implicit val arbMemberUp: Arbitrary[MemberUp] = Arbitrary(
    arbUpMember.arbitrary.map(MemberUp)
  )

  implicit val arbMemberLeft: Arbitrary[MemberLeft] = Arbitrary(
    arbLeavingMember.arbitrary.map(MemberLeft)
  )

  implicit val arbMemberExited: Arbitrary[MemberExited] = Arbitrary(
    arbExitingMember.arbitrary.map(MemberExited)
  )

  implicit val arbMemberDowned: Arbitrary[MemberDowned] = Arbitrary(
    arbDownMember.arbitrary.map(MemberDowned)
  )

  implicit val arbMemberWeaklyUp: Arbitrary[MemberWeaklyUp] = Arbitrary(
    arbWeaklyUpMember.arbitrary.map(MemberWeaklyUp)
  )

  implicit val arbMemberRemoved: Arbitrary[MemberRemoved] = Arbitrary(
    arbRemovedMember.arbitrary.map(MemberRemoved(_, Removed))
  )

  implicit val arbMemberEvent: Arbitrary[MemberEvent] = Arbitrary(
    oneOf(
      arbMemberJoined.arbitrary,
      arbMemberUp.arbitrary,
      arbMemberLeft.arbitrary,
      arbMemberExited.arbitrary,
      arbMemberDowned.arbitrary,
      arbMemberWeaklyUp.arbitrary,
      arbMemberRemoved.arbitrary
    )
  )

  implicit val arbUnreachableMember: Arbitrary[UnreachableMember] = Arbitrary(
    arbMember.arbitrary.map(UnreachableMember)
  )

  implicit val arbReachableMember: Arbitrary[ReachableMember] = Arbitrary(
    arbMember.arbitrary.map(ReachableMember)
  )

  implicit val arbReachabilityEvent: Arbitrary[ReachabilityEvent] = Arbitrary(
    oneOf(arbUnreachableMember.arbitrary, arbReachableMember.arbitrary)
  )

  implicit val arbDownReachable: Arbitrary[DownReachable] = Arbitrary(arbWorldView.arbitrary.map(DownReachable(_)))

  implicit val arbDownUnreachable: Arbitrary[DownUnreachable] = Arbitrary(
    arbWorldView.arbitrary.map(DownUnreachable(_))
  )

  implicit val arbDownSelf: Arbitrary[DownSelf] = Arbitrary(arbWorldView.arbitrary.map(DownSelf(_)))

  implicit val arbDownThese: Arbitrary[DownThese] = Arbitrary(
    for {
      decision1 <- oneOf(arbDownReachable.arbitrary, arbDownUnreachable.arbitrary, arbDownSelf.arbitrary) // todo also gen downtheses?
      decision2 <- oneOf(arbDownReachable.arbitrary, arbDownUnreachable.arbitrary, arbDownSelf.arbitrary)
    } yield DownThese(decision1, decision2)
  )

  implicit val arbStrategyDecision: Arbitrary[StrategyDecision] = Arbitrary(
    oneOf(arbDownReachable.arbitrary, arbDownUnreachable.arbitrary, arbDownSelf.arbitrary, arbDownThese.arbitrary)
  )
}

trait ArbitraryInstances0 {
  implicit def arbSortedSet[A: Arbitrary: Order]: Arbitrary[SortedSet[A]] =
    Arbitrary(arbitrary[Set[A]].map(s => SortedSet.empty[A](implicitly[Order[A]].toOrdering) ++ s))

  implicit def arbSortedMap[K: Arbitrary: Order, V: Arbitrary]: Arbitrary[SortedMap[K, V]] =
    Arbitrary(arbitrary[Map[K, V]].map(s => SortedMap.empty[K, V](implicitly[Order[K]].toOrdering) ++ s))

  implicit def arbNonEmptySet[A](implicit O: Order[A], A: Arbitrary[A]): Arbitrary[NonEmptySet[A]] =
    Arbitrary(implicitly[Arbitrary[SortedSet[A]]].arbitrary.flatMap(fa => A.arbitrary.map(a => NonEmptySet(a, fa))))

  implicit def arbNonEmptyMap[K, A](implicit O: Order[K],
                                    A: Arbitrary[A],
                                    K: Arbitrary[K]): Arbitrary[NonEmptyMap[K, A]] =
    Arbitrary(for {
      fa <- arbSortedMap[K, A].arbitrary
      k  <- K.arbitrary
      a  <- A.arbitrary
    } yield NonEmptyMap((k, a), fa))
}
