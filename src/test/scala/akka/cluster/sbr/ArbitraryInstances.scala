package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.{Member, MemberStatus, UniqueAddress, Reachability => _}
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import shapeless.tag
import shapeless.tag.@@

import scala.collection.immutable.SortedMap

object ArbitraryInstances extends ArbitraryInstances

trait ArbitraryInstances {
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

  implicit val arbWorldView: Arbitrary[WorldView] = Arbitrary(
    arbitrary[Seq[(Member, Reachability)]].map(m => new WorldView(SortedMap(m: _*)))
  )

  implicit val arbHealthyWorldView: Arbitrary[HealthyWorldView] = Arbitrary(
    for {
      members <- arbitrary[Seq[Member]]
      member <- arbitrary[Member]
      all = (member +: members).map(_ -> Reachable)
      worldView = new WorldView(SortedMap(all: _*))
    } yield tag[HealthyTag][WorldView](worldView)
  )

  implicit val arbUpNumberConsistentWorldView: Arbitrary[UpNumberConsistentWorldView] = Arbitrary {
    for {
      members <- arbitrary[Seq[WeaklyUpMember]]
      member <- arbitrary[WeaklyUpMember]

      all = (member +: members).zipWithIndex.map {
        case (weaklyUpMember, ix) => weaklyUpMember.copyUp(ix) -> Reachable
      }

      worldView = new WorldView(SortedMap(all: _*))
    } yield tag[UpNumberConsistentTag][WorldView](worldView)
  }

  implicit val arbReachability: Arbitrary[Reachability] =
    Arbitrary(oneOf(Reachable, Unreachable))

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
    arbitrary[JoiningMember].map(m => tag[WeaklyUpTag][Member](m.copy(WeaklyUp)))
  )

  implicit val arbUpMember: Arbitrary[UpMember] = Arbitrary(
    arbitrary[JoiningMember].map(m => tag[UpTag][Member](m.copyUp(m.hashCode())))
  )

  implicit val arbLeavingMember: Arbitrary[LeavingMember] = Arbitrary(
    arbitrary[JoiningMember].map(m => tag[LeavingTag][Member](m.copy(Leaving)))
  )

  implicit val arbDownMember: Arbitrary[DownMember] = Arbitrary(
    arbitrary[JoiningMember].map(m => tag[DownTag][Member](m.copy(Down)))
  )

  implicit val arbRemovedMember: Arbitrary[RemovedMember] = Arbitrary(
    arbitrary[JoiningMember].map(m => tag[RemovedTag][Member](m.copy(Removed)))
  )

  implicit val arbExitingMember: Arbitrary[ExitingMember] = Arbitrary(
    arbitrary[LeavingMember].map(m => tag[ExitingTag][Member](m.copy(Exiting)))
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

  implicit val arbReachableNode: Arbitrary[ReachableNode] =
    Arbitrary(arbMember.arbitrary.map(ReachableNode(_)))

  implicit val arbUnreachableNode: Arbitrary[UnreachableNode] =
    Arbitrary(arbMember.arbitrary.map(UnreachableNode(_)))

  implicit val arbUniqueAddress: Arbitrary[UniqueAddress] =
    Arbitrary(for {
      address <- arbitrary[Address]
      longUid <- arbitrary[Long]
    } yield UniqueAddress(address, longUid))

  implicit val arbAddress: Arbitrary[Address] =
    Arbitrary(for {
      protocol <- alphaNumStr
      system <- alphaNumStr
    } yield Address(protocol, system, None, None))

  implicit val arbMemberStatusFromJoining: Arbitrary[MemberStatus] =
    Arbitrary(oneOf[MemberStatus](WeaklyUp, WeaklyUp))

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
    arbitrary[Member].map(UnreachableMember)
  )

  implicit val arbReachableMember: Arbitrary[ReachableMember] = Arbitrary(
    arbitrary[Member].map(ReachableMember)
  )

  implicit val arbReachabilityEvent: Arbitrary[ReachabilityEvent] = Arbitrary(
    oneOf(arbitrary[UnreachableMember], arbitrary[ReachableMember])
  )
}
