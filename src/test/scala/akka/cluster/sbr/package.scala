package akka.cluster

import akka.actor.Address
import akka.cluster.{Reachability => _}
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import shapeless.tag
import shapeless.tag.@@

import scala.collection.immutable.SortedSet

package object sbr {
  sealed trait JoiningTag
  sealed trait WeaklyUpTag
  sealed trait UpTag
  sealed trait LeavingTag
  sealed trait ExitingTag
  sealed trait DownTag
  sealed trait RemovedTag

  type JoiningMember = Member @@ JoiningTag
  type WeaklyUpMember = Member @@ WeaklyUpTag
  type UpMember = Member @@ UpTag
  type LeavingMember = Member @@ LeavingTag
  type ExitingMember = Member @@ ExitingTag
  type DownMember = Member @@ DownTag
  type RemovedMember = Member @@ RemovedTag

  implicit val arbReachibility: Arbitrary[Reachability] = Arbitrary(
    for {
      reachableNodes <- arbitrary[SortedSet[Member]]
      unreachableNodes <- arbitrary[Set[Member]]
    } yield Reachability(reachableNodes, unreachableNodes)
  )

  implicit val arbJoiningMember: Arbitrary[JoiningMember] =
    Arbitrary(for {
      uniqueAddress <- arbitrary[UniqueAddress]
      datacenter <- arbitrary[String]
    } yield tag[JoiningTag][Member](Member(uniqueAddress, Set(s"dc-$datacenter"))))

  implicit val arbWeaklyUpMember: Arbitrary[WeaklyUpMember] = Arbitrary(
    arbJoiningMember.arbitrary.map(m => tag[WeaklyUpTag][Member](m.copy(WeaklyUp)))
  )

  implicit val arbUpMember: Arbitrary[UpMember] = Arbitrary(
    arbJoiningMember.arbitrary.map(m => tag[UpTag][Member](m.copy(Up)))
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
    Gen.oneOf(
      arbWeaklyUpMember.arbitrary,
      arbUpMember.arbitrary,
      arbLeavingMember.arbitrary,
      arbDownMember.arbitrary,
      arbRemovedMember.arbitrary,
      arbExitingMember.arbitrary
    )
  )

  implicit val arbUniqueAddress: Arbitrary[UniqueAddress] =
    Arbitrary(for {
      address <- arbitrary[Address]
      longUid <- arbitrary[Long]
    } yield UniqueAddress(address, longUid))

  implicit val arbAddress: Arbitrary[Address] =
    Arbitrary(for {
      protocol <- arbitrary[String]
      system <- arbitrary[String]
    } yield Address(protocol, system, None, None))

  implicit val arbMemberStatusFromJoining: Arbitrary[MemberStatus] =
    Arbitrary(Gen.oneOf[MemberStatus](WeaklyUp, WeaklyUp))

  implicit val arbMemberJoined: Arbitrary[MemberJoined] = Arbitrary(
    arbitrary[JoiningMember].map(MemberJoined)
  )

  implicit val arbMemberUp: Arbitrary[MemberUp] = Arbitrary(
    arbitrary[UpMember].map(MemberUp)
  )

  implicit val arbMemberLeft: Arbitrary[MemberLeft] = Arbitrary(
    arbitrary[LeavingMember].map(MemberLeft)
  )

  implicit val arbMemberExited: Arbitrary[MemberExited] = Arbitrary(
    arbitrary[ExitingMember].map(MemberExited)
  )

  implicit val arbMemberDowned: Arbitrary[MemberDowned] = Arbitrary(
    arbitrary[DownMember].map(MemberDowned)
  )

  implicit val arbMemberWeaklyUp: Arbitrary[MemberWeaklyUp] = Arbitrary(
    arbitrary[WeaklyUpMember].map(MemberWeaklyUp)
  )

  implicit val arbMemberRemoved: Arbitrary[MemberRemoved] = Arbitrary(
    arbitrary[RemovedMember].map(MemberRemoved(_, Removed))
  )

  implicit val arbMemberEvent: Arbitrary[MemberEvent] = Arbitrary(
    Gen.oneOf(
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
    arbitrary[Member].map(UnreachableMember)
  )

  implicit val arbReachableMember: Arbitrary[ReachableMember] = Arbitrary(
    arbitrary[Member].map(ReachableMember)
  )

  implicit val arbReachabilityEvent: Arbitrary[ReachabilityEvent] = Arbitrary(
    Gen.oneOf(arbUnreachableMember.arbitrary, arbReachableMember.arbitrary)
  )
}
