package com.swissborg.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus.{Joining, Removed, WeaklyUp}
import akka.cluster.{Member, UniqueAddress}
import cats.data.NonEmptySet
import cats.implicits._
import cats.kernel.Eq
import com.swissborg.sbr.WorldView.Status
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.reachability._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import scala.collection.immutable.SortedSet

/**
  * Represents the view of the cluster from the point of view of the
  * `selfNode`.
  */
private[sbr] final case class WorldView private (
    selfUniqueAddress: UniqueAddress,
    selfStatus: Status,
    /**
      * The ordering on nodes is defined on their unique address,
      * ignoring for instance the status.
      * As a result, it cannot contain duplicate nodes.
      *
      * Care needs need to be taken when replacing a node with one where
      * the status changed in the set. First it has it to be removed and
      * then added. Only adding it will not override the value as they
      * are equal given the ordering.
      */
    otherMembersStatus: Map[UniqueAddress, Status]
) {
  import WorldView._

  assert(
    !otherMembersStatus.contains(selfUniqueAddress),
    s"The current member  shoud not appear in the other members! $otherMembersStatus <- $selfUniqueAddress"
  )

  lazy val selfNode: Node = toNode(selfStatus.member, selfStatus.reachability)

  /**
    * All the nodes in the cluster.
    */
  lazy val nodes: NonEmptySet[Node] = {
    val otherNodes: Seq[Node] = otherMembersStatus.values.map {
      case Status(member, reachability) => toNode(member, reachability)
    }(collection.breakOut)

    NonEmptySet.of(selfNode, otherNodes: _*)
  }

  lazy val members: NonEmptySet[Member] = nodes.map(_.member)

  lazy val nonJoiningNodes: SortedSet[Node] = nodes.filter(isNonJoining)

  def nonJoiningNodesWithRole(role: String): SortedSet[Node] =
    if (role.nonEmpty) nonJoiningNodes.filter(_.member.roles.contains(role)) else nonJoiningNodes

  lazy val nonJoiningNonICNodes: SortedSet[NonIndirectlyConnectedNode] = nodes.collect {
    case node: NonIndirectlyConnectedNode if isNonJoining(node) => node
  }

  def nonJoiningNonICNodesWithRole(role: String): SortedSet[NonIndirectlyConnectedNode] =
    if (role.nonEmpty) nonJoiningNonICNodes.filter(_.member.roles.contains(role))
    else nonJoiningNonICNodes

  lazy val nonJoiningReachableNodes: SortedSet[ReachableNode] = reachableNodes.collect {
    case n if isNonJoining(n) => n
  }

  def nonJoiningReachableNodesWithRole(role: String): SortedSet[ReachableNode] =
    if (role.nonEmpty) nonJoiningReachableNodes.filter(_.member.roles.contains(role))
    else nonJoiningReachableNodes

  /**
    * All the reachable nodes.
    */
  lazy val reachableNodes: SortedSet[ReachableNode] = nodes.collect { case r: ReachableNode => r }

  /**
    * All the unreachable nodes.
    */
  lazy val unreachableNodes: SortedSet[UnreachableNode] = nodes.collect {
    case r: UnreachableNode => r
  }

  /**
    * The indirectly connected nodes with the given role.
    */
  def indirectlyConnectedNodesWithRole(role: String): SortedSet[IndirectlyConnectedNode] =
    if (role.nonEmpty) indirectlyConnectedNodes.filter(_.member.roles.contains(role))
    else indirectlyConnectedNodes

  /**
    * All the indirectly connected nodes.
    */
  lazy val indirectlyConnectedNodes: SortedSet[IndirectlyConnectedNode] = nodes.collect {
    case r: IndirectlyConnectedNode => r
  }

  lazy val nonJoiningUnreachableNodes: SortedSet[UnreachableNode] = unreachableNodes.collect {
    case n if isNonJoining(n) => n
  }

  def unreachableNodesWithRole(role: String): SortedSet[UnreachableNode] =
    if (role.nonEmpty) unreachableNodes.filter(_.member.roles.contains(role))
    else unreachableNodes

  def nonJoiningUnreachableNodesWithRole(role: String): SortedSet[UnreachableNode] =
    if (role.nonEmpty) nonJoiningUnreachableNodes.filter(_.member.roles.contains(role))
    else nonJoiningUnreachableNodes

  def addOrUpdate(member: Member): WorldView = sameDataCenter(member) {
    if (member.uniqueAddress === selfUniqueAddress) {
      copy(selfStatus = selfStatus.withMember(member))
    } else {
      otherMembersStatus
        .get(member.uniqueAddress)
        .fold(
          // Assumes the member is reachable if seen for the 1st time.
          copy(
            otherMembersStatus = otherMembersStatus + (member.uniqueAddress -> Status(
              member,
              SBReachabilityStatus.Reachable
            ))
          )
        )(
          s =>
            copy(
              otherMembersStatus = otherMembersStatus - member.uniqueAddress + (member.uniqueAddress -> s
                .withMember(member))
            )
        )
    }
  }

  def removeMember(member: Member): WorldView = sameDataCenter(member) {
    if (member.uniqueAddress === selfUniqueAddress) {
      copy(member.uniqueAddress, selfStatus = selfStatus.withMember(member)) // ignore only update // todo is it safe?
    } else {
      otherMembersStatus
        .get(member.uniqueAddress)
        .fold(this) { _ =>
          copy(otherMembersStatus = otherMembersStatus - member.uniqueAddress)
        }
    }
  }

  /**
    * Change the `node`'s state to `Reachable`.
    */
  def withReachableNode(node: UniqueAddress): WorldView =
    changeReachability(node, SBReachabilityStatus.Reachable)

  /**
    * Change the `node`'s status to `Unreachable`.
    */
  def withUnreachableNode(node: UniqueAddress): WorldView =
    changeReachability(node, SBReachabilityStatus.Unreachable)

  /**
    * Change the `node`'s status to `IndirectlyConnected`.
    */
  def withIndirectlyConnectedNode(node: UniqueAddress): WorldView =
    changeReachability(node, SBReachabilityStatus.IndirectlyConnected)

  /**
    * Replace the `selfMember` with `member`.
    *
    * Used in tests.
    */
  private[sbr] def changeSelf(member: Member): WorldView = sameDataCenter(member) {
    val newSelfStatus = otherMembersStatus
      .get(member.uniqueAddress)
      .fold(Status(member, SBReachabilityStatus.Reachable)) { m =>
        m.withMember(member)
      }
      .withReachability(SBReachabilityStatus.Reachable)

    selfStatus.member.status match {
      case Removed =>
        copy(
          selfUniqueAddress = member.uniqueAddress,
          selfStatus = newSelfStatus,
          otherMembersStatus = otherMembersStatus - member.uniqueAddress
        )

      case _ =>
        val maybeOldSelf = if (selfUniqueAddress === member.uniqueAddress) {
          Map.empty
        } else {
          Map(selfUniqueAddress -> selfStatus)
        }

        val updatedOtherMembersStatus = otherMembersStatus - member.uniqueAddress ++ maybeOldSelf

        copy(
          selfUniqueAddress = member.uniqueAddress,
          selfStatus = newSelfStatus,
          otherMembersStatus = updatedOtherMembersStatus
        )
    }
  }

  /**
    * Change the reachability of `member` with `reachability`.
    */
  private def changeReachability(
      node: UniqueAddress,
      reachability: SBReachabilityStatus
  ): WorldView =
    if (node === selfUniqueAddress) {
      copy(selfUniqueAddress, selfStatus = selfStatus.withReachability(reachability))
    } else {
      otherMembersStatus
        .get(node)
        .fold(this) { s =>
          copy(
            otherMembersStatus = otherMembersStatus - node + (node -> s
              .withReachability(reachability))
          )
        }
    }

  /**
    * True when the node is non-joining.
    */
  private def isNonJoining(node: Node): Boolean =
    node.member.status != Joining && node.member.status != WeaklyUp

  /**
    * Update the world view if and only if the `member` is in the same datacenter.
    */
  private def sameDataCenter(member: Member)(updatedWorldView: => WorldView): WorldView =
    if (selfStatus.member.dataCenter == member.dataCenter) {
      updatedWorldView
    } else {
      this
    }

  lazy val simple: Simple = Simple(
    selfUniqueAddress,
    reachableNodes.toList.map(n => SimpleMember.fromMember(n.member)),
    indirectlyConnectedNodes.toList.map(n => SimpleMember.fromMember(n.member)),
    unreachableNodes.toList.map(n => SimpleMember.fromMember(n.member))
  )
}

private[sbr] object WorldView {
  final case class Simple(
      selfUniqueAddress: UniqueAddress,
      reachableMembers: List[SimpleMember],
      indirectlyConnectedMembers: List[SimpleMember],
      unreachableMembers: List[SimpleMember]
  )

  object Simple {
    implicit val simpleWorldViewEncoder: Encoder[Simple] = deriveEncoder
    implicit val simpleWorldEq: Eq[Simple] = Eq.and(
      Eq.and(
        Eq.and(Eq.by(_.reachableMembers), Eq.by(_.unreachableMembers)),
        Eq.by(_.indirectlyConnectedMembers)
      ),
      Eq.by(_.selfUniqueAddress)
    )
  }

  /**
    * Create an empty world view.
    */
  def init(selfMember: Member): WorldView =
    new WorldView(
      selfMember.uniqueAddress,
      Status(selfMember, SBReachabilityStatus.Reachable),
      Map.empty
    )

  /**
    * Create a world view based on the `state`.
    * All the members not in the same data-center as `selfMember`
    * will be ignored.
    */
  def fromSnapshot(selfMember: Member, snapshot: CurrentClusterState): WorldView = {
    val sameDCMembers = snapshot.members.filter(_.dataCenter == selfMember.dataCenter)
    val sameDCUnreachable = snapshot.unreachable.filter(_.dataCenter == selfMember.dataCenter)

    val latestSelfMember = sameDCMembers.find(_.uniqueAddress === selfMember.uniqueAddress)
    val otherMembers = latestSelfMember.fold(sameDCMembers)(sameDCMembers - _)

    // Initiate the world view with the current
    // cluster member. The snapshot contains its
    // latest state. If not, use the provided the
    // one, which should be a placeholder where
    // it resides in the removed state.
    val w = latestSelfMember.fold(WorldView.init(selfMember))(WorldView.init)

    // add reachable members to the world view
    val w1 = (otherMembers -- sameDCUnreachable).foldLeft(w) {
      case (w, member) =>
        member.status match {
          case Removed => w.removeMember(member)
          case _       => w.addOrUpdate(member).withReachableNode(member.uniqueAddress)
        }
    }

    // add unreachable members to the world view
    sameDCUnreachable // selfMember cannot be unreachable
      .foldLeft(w1) {
        case (w, member) =>
          member.status match {
            case Removed => w.removeMember(member)
            case _       => w.addOrUpdate(member).withUnreachableNode(member.uniqueAddress)
          }
      }
  }

  /**
    * Build a world view based on the given nodes.
    *
    * Used in tests.
    */
  def fromNodes(selfNode: Node, otherNodes: SortedSet[Node]): WorldView = {
    val sameDCOtherNodes = otherNodes.filter(_.member.dataCenter == selfNode.member.dataCenter)

    def convert(node: Node): (UniqueAddress, Status) =
      node.member.uniqueAddress -> (node match {
        case _: UnreachableNode => Status(node.member, SBReachabilityStatus.Unreachable)
        case _: ReachableNode   => Status(node.member, SBReachabilityStatus.Reachable)
        case _: IndirectlyConnectedNode =>
          Status(node.member, SBReachabilityStatus.IndirectlyConnected)
      })

    assert(!sameDCOtherNodes.contains(selfNode))

    val (selfUniqueAddress, selfStatus) = convert(selfNode)

    val (_, others) = sameDCOtherNodes.partition(_.member.status === Removed)

    WorldView(
      selfUniqueAddress,
      selfStatus,
      others.filterNot(_.member.status === Removed).map(convert)(collection.breakOut)
    )
  }

  /**
    * Convert the `member` and its `reachability` to a [[Node]].
    */
  private def toNode(member: Member, reachability: SBReachabilityStatus): Node =
    reachability match {
      case SBReachabilityStatus.Reachable           => ReachableNode(member)
      case SBReachabilityStatus.Unreachable         => UnreachableNode(member)
      case SBReachabilityStatus.IndirectlyConnected => IndirectlyConnectedNode(member)
    }

  final case class Status(member: Member, reachability: SBReachabilityStatus) {
    def withReachability(updatedReachability: SBReachabilityStatus): Status =
      copy(reachability = updatedReachability)

    def withMember(updatedMember: Member): Status = copy(member = updatedMember)
  }
}
