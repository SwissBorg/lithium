package com.swissborg.sbr.instances

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.swissborg.AkkaArbitraryInstances._
import akka.cluster._
import cats._
import cats.data._
import com.swissborg.sbr._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.reachability._
import com.swissborg.sbr.strategy._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen.posNum
import org.scalacheck.{Arbitrary, Gen}
import shapeless.tag
import shapeless.tag.@@

import scala.collection.immutable.{SortedMap, SortedSet}

object ArbitraryTestInstances extends ArbitraryTestInstances

trait ArbitraryTestInstances extends ArbitraryInstances0 {
  sealed trait WeaklyUpTag
  type WeaklyUpMember = Member @@ WeaklyUpTag

  sealed trait JoiningOrWeaklyUpTag
  type JoiningOrWeaklyUpMember = Member @@ JoiningOrWeaklyUpTag

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

  sealed trait AllUpTag
  type AllUpWorldView = WorldView @@ AllUpTag

  sealed trait JoiningOrWeaklyUpOnlyTag
  type JoiningOrWeaklyUpOnlyWorldView = WorldView @@ JoiningOrWeaklyUpOnlyTag

  sealed trait NonRemovedMemberTag
  type NonRemovedMember = Member @@ NonRemovedMemberTag

  sealed trait NonRemovedReachableNodeTag
  type NonRemovedReachableNode = ReachableNode @@ NonRemovedReachableNodeTag

  implicit val arbWeaklyUpMember: Arbitrary[WeaklyUpMember] = Arbitrary(
    arbJoiningMember.arbitrary.map(m => tag[WeaklyUpTag][Member](m.copy(WeaklyUp)))
  )

  implicit val arbJoiningOrWeaklyUpMember: Arbitrary[JoiningOrWeaklyUpMember] = Arbitrary(
    Gen.oneOf(
      arbJoiningMember.arbitrary.map(tag[JoiningOrWeaklyUpTag][Member]),
      arbWeaklyUpMember.arbitrary.map(tag[JoiningOrWeaklyUpTag][Member])
    )
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
    Gen.oneOf(
      arbJoiningMember.arbitrary,
      arbWeaklyUpMember.arbitrary,
      arbUpMember.arbitrary,
      arbLeavingMember.arbitrary,
      arbDownMember.arbitrary,
//      arbRemovedMember.arbitrary,
      arbExitingMember.arbitrary
    )
  )

  implicit val arbWorldView: Arbitrary[WorldView] = Arbitrary(
    for {
      selfNode <- arbitrary[Node]
      nodes <- arbitrary[SortedSet[Node]]
      nodes0 = nodes - selfNode
    } yield WorldView.fromNodes(ReachableNode(selfNode.member), nodes0)
  )

  implicit val arbHealthyWorldView: Arbitrary[HealthyWorldView] = Arbitrary(
    for {
      selfNode <- arbitrary[ReachableNode]
      nodes <- arbitrary[SortedSet[ReachableNode]]
      nodes0 = nodes - selfNode
      worldView = WorldView.fromNodes(selfNode, nodes0.map(identity))
    } yield tag[HealthyTag][WorldView](worldView)
  )

  implicit val arbAllUpWorldView: Arbitrary[AllUpWorldView] = {
    Arbitrary(for {
      weaklyUpMembers <- arbNonEmptySet[Member @@ WeaklyUpTag].arbitrary

      // The head of the sorted set has the highest up-number
      // so it's not the oldest member. This creates problems
      // in the `OldestRemovedScenario` as removing the
      count = weaklyUpMembers.length
//      _ = println(weaklyUpMembers)
      upMembers = weaklyUpMembers.zipWithIndex.map {
        case (member, ix) => member.copyUp(count - ix)
      }
//      _ = println("...")
//      _ = println(upMembers)

      worldView = WorldView
        .fromNodes(ReachableNode(upMembers.head), upMembers.tail.map(ReachableNode(_)))
    } yield tag[AllUpTag][WorldView](worldView))
  }

  implicit val arbJoiningOrWeaklyUpOnlyWorldView: Arbitrary[JoiningOrWeaklyUpOnlyWorldView] =
    Arbitrary(
      for {
        selfNode <- arbitrary[Member @@ JoiningOrWeaklyUpTag]
        nodes <- arbitrary[SortedSet[Member @@ JoiningOrWeaklyUpTag]]
        nodes0 = nodes - selfNode
      } yield
        tag[JoiningOrWeaklyUpOnlyTag][WorldView](
          WorldView.fromNodes(ReachableNode(selfNode), nodes0.map(ReachableNode(_)))
        )
    )

  implicit val arbNode: Arbitrary[Node] =
    Arbitrary(
      Gen.oneOf(
        arbReachableNode.arbitrary,
        arbUnreachableNode.arbitrary,
        arbIndirectlyConnectedNode.arbitrary
      )
    )

  implicit val arbReachableNode: Arbitrary[ReachableNode] =
    Arbitrary(arbMember.arbitrary.map(ReachableNode(_)))

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
      protocol <- Gen.identifier
      system <- Gen.identifier
      host <- Gen.identifier
      port <- Gen.chooseNum(0, Integer.MAX_VALUE)
    } yield Address(protocol, system, Some(host), Some(port)))

  implicit val arbMemberStatus: Arbitrary[MemberStatus] =
    Arbitrary(
      Gen.oneOf(Joining, WeaklyUp, Up, Leaving, Exiting, Down, Removed)
    )

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
    arbMember.arbitrary.map(UnreachableMember)
  )

  implicit val arbReachableMember: Arbitrary[ReachableMember] = Arbitrary(
    arbMember.arbitrary.map(ReachableMember)
  )

  implicit val arbReachabilityEvent: Arbitrary[ReachabilityEvent] = Arbitrary(
    Gen.oneOf(arbUnreachableMember.arbitrary, arbReachableMember.arbitrary)
  )

  implicit val arbDownReachable: Arbitrary[Decision.DownReachable] = Arbitrary(
    arbWorldView.arbitrary.map(Decision.DownReachable(_))
  )

  implicit val arbDownUnreachable: Arbitrary[Decision.DownUnreachable] = Arbitrary(
    arbWorldView.arbitrary.map(Decision.DownUnreachable(_))
  )

  implicit val arbDownThese: Arbitrary[Decision.DownThese] = Arbitrary(
    for {
      decision1 <- Gen
        .oneOf(arbDownReachable.arbitrary, arbDownUnreachable.arbitrary) // todo also gen downtheses?
      decision2 <- Gen
        .oneOf(arbDownReachable.arbitrary, arbDownUnreachable.arbitrary)
    } yield Decision.DownThese(decision1, decision2)
  )

  implicit val arbStrategyDecision: Arbitrary[Decision] = Arbitrary(
    Gen.oneOf(
      arbDownReachable.arbitrary,
      arbDownUnreachable.arbitrary,
      arbDownThese.arbitrary
    )
  )

  implicit val arbSBRReachability: Arbitrary[SBReachabilityStatus] = Arbitrary(
    Gen.oneOf(
      SBReachabilityStatus.Reachable,
      SBReachabilityStatus.Unreachable,
      SBReachabilityStatus.IndirectlyConnected
    )
  )

  implicit val arbContention: Arbitrary[SBReachabilityReporter.Contention] = Arbitrary(
    for {
      protester <- arbitrary[UniqueAddress]
      observer <- arbitrary[UniqueAddress]
      subject <- arbitrary[UniqueAddress]
      version <- arbitrary[Long]
    } yield SBReachabilityReporter.Contention(protester, observer, subject, version)
  )

  implicit val arbContentionAck: Arbitrary[SBReachabilityReporter.ContentionAck] = Arbitrary(
    for {
      from <- arbitrary[UniqueAddress]
      observer <- arbitrary[UniqueAddress]
      subject <- arbitrary[UniqueAddress]
      version <- arbitrary[Long]
    } yield SBReachabilityReporter.ContentionAck(from, observer, subject, version)
  )

  implicit val arbQuorumSize: Arbitrary[Int Refined Positive] = Arbitrary {
    posNum[Int].map(refineV[Positive](_).right.get) // trust me
  }

  implicit val arbReachableNodes: Arbitrary[ReachableQuorum] = Arbitrary(
    for {
      worldView <- arbitrary[WorldView]
      quorumSize <- arbitrary[Int Refined Positive]
      role <- arbitrary[String]
    } yield ReachableQuorum(worldView, quorumSize, role)
  )

  implicit val arbUnreachableNodes: Arbitrary[UnreachableQuorum] = Arbitrary(
    for {
      worldView <- arbitrary[WorldView]
      quorumSize <- arbitrary[Int Refined Positive]
      role <- arbitrary[String]
    } yield UnreachableQuorum(worldView, quorumSize, role)
  )
}

trait ArbitraryInstances0 {
  implicit def arbSortedSet[A: Arbitrary: Order]: Arbitrary[SortedSet[A]] =
    Arbitrary(arbitrary[Set[A]].map(s => SortedSet.empty[A](implicitly[Order[A]].toOrdering) ++ s))

  implicit def arbSortedMap[K: Arbitrary: Order, V: Arbitrary]: Arbitrary[SortedMap[K, V]] =
    Arbitrary(
      arbitrary[Map[K, V]].map(s => SortedMap.empty[K, V](implicitly[Order[K]].toOrdering) ++ s)
    )

  implicit def arbNonEmptySet[A](implicit O: Order[A], A: Arbitrary[A]): Arbitrary[NonEmptySet[A]] =
    Arbitrary(
      implicitly[Arbitrary[SortedSet[A]]].arbitrary
        .flatMap(fa => A.arbitrary.map(a => NonEmptySet(a, fa)))
    )

  implicit def arbNonEmptyMap[K, A](
      implicit O: Order[K],
      A: Arbitrary[A],
      K: Arbitrary[K]
  ): Arbitrary[NonEmptyMap[K, A]] =
    Arbitrary(for {
      fa <- arbSortedMap[K, A].arbitrary
      k <- K.arbitrary
      a <- A.arbitrary
    } yield NonEmptyMap((k, a), fa))
}
