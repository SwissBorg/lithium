package com.swissborg.lithium

import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.{Member, UniqueAddress}
import cats.data.NonEmptySet
import cats.implicits._
import cats.kernel.Eq
import com.swissborg.lithium.WorldView.OtherStatus.OtherStatus
import com.swissborg.lithium.WorldView.SelfStatus.SelfStatus
import com.swissborg.lithium.implicits._
import com.swissborg.lithium.reachability._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import scala.collection.immutable.SortedSet

/**
 * Represents the view of the cluster from the point of view of the
 * `selfNode`.
 *
 * Only contains nodes in the same data-center as the `selfNode`.
 * As a result, partitions between data-centers are not handled.
 */
final private[lithium] case class WorldView private (selfUniqueAddress: UniqueAddress,
                                                     selfStatus: SelfStatus,
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
                                                     otherMembersStatus: Map[UniqueAddress, OtherStatus]) {
  import WorldView._

  assert(
    !otherMembersStatus.contains(selfUniqueAddress),
    s"The current member should not appear in the other members! $otherMembersStatus <- $selfUniqueAddress"
  )

  lazy val selfNode: Node = toNode(selfStatus.member, selfStatus.reachability)

  /**
   * All the nodes in the cluster.
   */
  lazy val nodes: NonEmptySet[Node] = {
    val otherNodes: Seq[Node] = otherMembersStatus.values.map {
      case Status(member, reachability) => toNode(member, reachability)
    }.toSeq
    NonEmptySet.of(selfNode, otherNodes: _*)
  }

  lazy val members: NonEmptySet[Member] = nodes.map(_.member)

  lazy val nonICNodes: SortedSet[NonIndirectlyConnectedNode] = nodes.collect {
    case node: NonIndirectlyConnectedNode => node
  }

  def nonICNodesWithRole(role: String): SortedSet[NonIndirectlyConnectedNode] =
    if (role.nonEmpty) nonICNodes.filter(_.member.roles.contains(role))
    else nonICNodes

  /**
   * All the reachable nodes.
   */
  lazy val reachableNodes: SortedSet[ReachableNode] = nodes.collect { case r: ReachableNode => r }

  def reachableNodesWithRole(role: String): SortedSet[ReachableNode] =
    if (role.nonEmpty) reachableNodes.filter(_.member.roles.contains(role))
    else reachableNodes

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

  def unreachableNodesWithRole(role: String): SortedSet[UnreachableNode] =
    if (role.nonEmpty) unreachableNodes.filter(_.member.roles.contains(role))
    else unreachableNodes

  def status(node: UniqueAddress): Option[ReachabilityStatus] =
    if (node === selfUniqueAddress) {
      Some(selfStatus.reachability)
    } else {
      otherMembersStatus.get(node).map(_.reachability)
    }

  def addOrUpdate(member: Member): WorldView = sameDataCenter(member) {
    if (member.uniqueAddress === selfUniqueAddress) {
      copy(selfStatus = selfStatus.withMember(member))
    } else {
      otherMembersStatus
        .get(member.uniqueAddress)
        .fold(
          // Assumes the member is reachable if seen for the 1st time.
          copy(
            otherMembersStatus = otherMembersStatus + (member.uniqueAddress -> Status(member,
                                                                                      ReachabilityStatus.Reachable))
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
      otherMembersStatus.get(member.uniqueAddress).fold(this) { _ =>
        copy(otherMembersStatus = otherMembersStatus - member.uniqueAddress)
      }
    }
  }

  /**
   * Change the `node`'s state to `Reachable`.
   */
  def withReachableNode(node: UniqueAddress): WorldView =
    changeReachability(node, ReachabilityStatus.Reachable)

  /**
   * Change the `node`'s status to `Unreachable`.
   */
  def withUnreachableNode(node: UniqueAddress): WorldView =
    changeReachability(node, ReachabilityStatus.Unreachable)

  /**
   * Change the `node`'s status to `IndirectlyConnected`.
   */
  def withIndirectlyConnectedNode(node: UniqueAddress): WorldView =
    changeReachability(node, ReachabilityStatus.IndirectlyConnected)

  /**
   * Replace the `selfMember` with `member`.
   *
   * Used in tests.
   */
  private[lithium] def changeSelf(member: Member): WorldView = sameDataCenter(member) {
    val newSelfStatus: SelfStatus = otherMembersStatus
      .get(member.uniqueAddress)
      .fold(SelfStatus(member, ReachabilityStatus.Reachable)) { m =>
        m.reachability match {
          case reachability: SelfReachabilityStatus => SelfStatus(member, reachability)
          case _                                    => SelfStatus(member, ReachabilityStatus.Reachable)
        }
      }
      .withReachability(ReachabilityStatus.Reachable)

    selfStatus.member.status match {
      case Removed =>
        copy(selfUniqueAddress = member.uniqueAddress,
             selfStatus = newSelfStatus,
             otherMembersStatus = otherMembersStatus - member.uniqueAddress)

      case _ =>
        val maybeOldSelf: Map[UniqueAddress, OtherStatus] =
          if (selfUniqueAddress === member.uniqueAddress) Map.empty
          else Map(selfUniqueAddress -> selfStatus.widen)

        val updatedOtherMembersStatus = otherMembersStatus - member.uniqueAddress ++ maybeOldSelf

        copy(selfUniqueAddress = member.uniqueAddress,
             selfStatus = newSelfStatus,
             otherMembersStatus = updatedOtherMembersStatus)
    }
  }

  /**
   * Change the reachability of `member` with `reachability`.
   */
  private def changeReachability(node: UniqueAddress, reachability: ReachabilityStatus): WorldView =
    if (node === selfUniqueAddress) {
      reachability match {
        case reachability: SelfReachabilityStatus =>
          copy(selfUniqueAddress, selfStatus = selfStatus.withReachability(reachability))
        case _ => this // TODO is it safe to ignore?
      }
    } else {
      otherMembersStatus.get(node).fold(this) { s =>
        copy(otherMembersStatus = otherMembersStatus - node + (node -> s.withReachability(reachability)))
      }
    }

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

private[lithium] object WorldView {
  final case class Simple(selfUniqueAddress: UniqueAddress,
                          reachableMembers: List[SimpleMember],
                          indirectlyConnectedMembers: List[SimpleMember],
                          unreachableMembers: List[SimpleMember])

  object Simple {
    implicit val simpleWorldViewEncoder: Encoder[Simple] = deriveEncoder
    implicit val simpleWorldEq: Eq[Simple] = Eq.and(
      Eq.and(Eq.and(Eq.by(_.reachableMembers), Eq.by(_.unreachableMembers)), Eq.by(_.indirectlyConnectedMembers)),
      Eq.by(_.selfUniqueAddress)
    )
  }

  /**
   * Create an empty world view.
   */
  def init(selfMember: Member): WorldView =
    new WorldView(selfMember.uniqueAddress, Status(selfMember, ReachabilityStatus.Reachable), Map.empty)

  /**
   * Create a world view based on the `state`.
   * All the members not in the same data-center as `selfMember`
   * will be ignored.
   */
  def fromSnapshot(selfMember: Member, snapshot: CurrentClusterState): WorldView = {
    val sameDCMembers     = snapshot.members.filter(_.dataCenter == selfMember.dataCenter)
    val sameDCUnreachable = snapshot.unreachable.filter(_.dataCenter == selfMember.dataCenter)

    val latestSelfMember = sameDCMembers.find(_.uniqueAddress === selfMember.uniqueAddress)
    val otherMembers     = latestSelfMember.fold(sameDCMembers)(sameDCMembers - _)

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

    def convertSelf(selfNode: Node): (UniqueAddress, SelfStatus) =
      selfNode.uniqueAddress -> (selfNode match {
        case ReachableNode(member) =>
          SelfStatus(member, ReachabilityStatus.Reachable)

        case IndirectlyConnectedNode(member) =>
          SelfStatus(member, ReachabilityStatus.IndirectlyConnected)

        case UnreachableNode(member) =>
          SelfStatus(member, ReachabilityStatus.Reachable) // TODO is this a good default?
      })

    def convertOther(node: Node): (UniqueAddress, OtherStatus) =
      node.uniqueAddress -> (node match {
        case UnreachableNode(member) =>
          OtherStatus(member, ReachabilityStatus.Unreachable)

        case ReachableNode(member) =>
          OtherStatus(member, ReachabilityStatus.Reachable)

        case IndirectlyConnectedNode(member) =>
          OtherStatus(member, ReachabilityStatus.IndirectlyConnected)
      })

    assert(!sameDCOtherNodes.contains(selfNode))

    val (selfUniqueAddress, selfStatus) = convertSelf(selfNode)

    val (_, others) = sameDCOtherNodes.partition(_.member.status === Removed)

    WorldView(selfUniqueAddress, selfStatus, others.view.filterNot(_.member.status === Removed).map(convertOther).toMap)
  }

  /**
   * Convert the `member` and its `reachability` to a [[Node]].
   */
  private def toNode[R <: ReachabilityStatus](member: Member, reachability: R): Node =
    reachability match {
      case ReachabilityStatus.Reachable           => ReachableNode(member)
      case ReachabilityStatus.Unreachable         => UnreachableNode(member)
      case ReachabilityStatus.IndirectlyConnected => IndirectlyConnectedNode(member)
    }

  final case class Status[R <: ReachabilityStatus](member: Member, reachability: R) {

    def withReachability[R0 <: R](updatedReachability: R0): Status[R0] =
      copy(reachability = updatedReachability)

    def withMember(updatedMember: Member): Status[R] =
      Status(updatedMember, reachability)

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def widen[R0 >: R <: ReachabilityStatus]: Status[R0] = this.asInstanceOf[Status[R0]]
  }

  object SelfStatus {
    type SelfStatus = Status[SelfReachabilityStatus]

    def apply(member: Member, reachability: SelfReachabilityStatus): SelfStatus =
      Status(member, reachability)
  }

  object OtherStatus {
    type OtherStatus = Status[ReachabilityStatus]

    def apply(member: Member, reachability: ReachabilityStatus): OtherStatus =
      Status(member, reachability)
  }

  /**
   * True if the node is "joining" or "weakly-up".
   *
   * Members can still join the cluster during partitions.
   */
  def isJoining(node: Node): Boolean =
    node.member.status === Joining || node.member.status === WeaklyUp

  /**
   * True if the node can be removed by the leader while it is unreachable.
   *
   * Unreachable members that are "exiting" or "down" are removed from the membership
   * state even during a split.
   */
  def canBeRemoveWhileUnreachable(node: Node): Boolean =
    node.member.status === Exiting || node.member.status === Down

  def isConsideredNode(node: Node): Boolean =
    !isJoining(node) && !canBeRemoveWhileUnreachable(node)
}
