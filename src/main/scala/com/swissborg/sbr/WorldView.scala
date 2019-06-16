package com.swissborg.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus.{Joining, Removed, WeaklyUp}
import akka.cluster.{Member, UniqueAddress}
import cats.data.NonEmptySet
import cats.implicits._
import cats.kernel.Eq
import com.swissborg.sbr.WorldView.Status
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.reachability.SBReachabilityReporter.SBReachabilityStatus.{
  IndirectlyConnected,
  Reachable,
  Unreachable
}
import com.swissborg.sbr.reachability.SBReachabilityReporter._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Represents the view of the cluster from the point of view of the
  * `selfNode`.
  */
private[sbr] final case class WorldView private (
    val selfUniqueAddress: UniqueAddress,
    val selfStatus: Status,
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
    val otherMembersStatus: Map[UniqueAddress, Status]
) {
  import WorldView._

  assert(
    !otherMembersStatus.contains(selfUniqueAddress),
    s"$otherMembersStatus <- $selfUniqueAddress"
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

  lazy val consideredNodes: Set[Node] = nodes.filter(shouldBeConsidered)

  def consideredNodesWithRole(role: String): Set[Node] =
    if (role.nonEmpty) consideredNodes.filter(_.member.roles.contains(role)) else consideredNodes

  /**
    * The nodes that need to be considered in split-brain resolutions.
    *
    * A node is to be considered when it isn't in the "Joining" or "WeaklyUp"
    * states. These status are ignored since a node can join and become
    * weakly-up during a network-partition.
    */
  lazy val consideredCleanNodes: Set[CleanNode] = nodes.collect {
    case node: CleanNode if shouldBeConsidered(node) => node
  }

  /**
    * The nodes with the given role, that need to be considered in
    * split-brain resolutions.
    */
  def consideredCleanNodesWithRole(role: String): Set[CleanNode] =
    if (role.nonEmpty) consideredCleanNodes.filter(_.member.roles.contains(role))
    else consideredCleanNodes

  /**
    * The reachable nodes that need to be considered in split-brain resolutions.
    */
  lazy val consideredReachableNodes: Set[ReachableNode] = reachableNodes.collect {
    case n if shouldBeConsidered(n) => n
  }

  /**
    * The reachable nodes with the given role, that need to be
    * considered in split-brain resolutions.
    */
  def consideredReachableNodesWithRole(role: String): Set[ReachableNode] =
    if (role.nonEmpty) consideredReachableNodes.filter(_.member.roles.contains(role))
    else consideredReachableNodes

  /**
    * All the reachable nodes.
    */
  lazy val reachableNodes: Set[ReachableNode] = nodes.collect { case r: ReachableNode => r }

  /**
    * All the unreachable nodes.
    */
  lazy val unreachableNodes: Set[UnreachableNode] = nodes.collect { case r: UnreachableNode => r }

  /**
    * The indirectly connected nodes with the given role.
    */
  def indirectlyConnectedNodesWithRole(role: String): Set[IndirectlyConnectedNode] =
    if (role.nonEmpty) indirectlyConnectedNodes.filter(_.member.roles.contains(role))
    else indirectlyConnectedNodes

  /**
    * All the indirectly connected nodes.
    */
  lazy val indirectlyConnectedNodes: Set[IndirectlyConnectedNode] = nodes.collect {
    case r: IndirectlyConnectedNode => r
  }

  /**
    * The unreachable nodes that need to be considered in split-brain resolutions.
    */
  lazy val consideredUnreachableNodes: Set[UnreachableNode] = unreachableNodes.collect {
    case n if shouldBeConsidered(n) => n
  }

  /**
    * The unreachable nodes with the given role, that need to be
    * considered in split-brain resolutions.
    */
  def consideredUnreachableNodesWithRole(role: String): Set[UnreachableNode] =
    if (role.nonEmpty) consideredUnreachableNodes.filter(_.member.roles.contains(role))
    else consideredUnreachableNodes

  def addOrUpdate(member: Member): WorldView =
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
              Reachable
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

  def removeMember(member: Member): WorldView =
    if (member.uniqueAddress === selfUniqueAddress) {
      copy(member.uniqueAddress, selfStatus = selfStatus.withMember(member)) // ignore only update // todo is it safe?
    } else {
      otherMembersStatus
        .get(member.uniqueAddress)
        .fold(this) { _ =>
          copy(otherMembersStatus = otherMembersStatus - member.uniqueAddress)
        }
    }

  /**
    * Change the `node`'s state to `Reachable`.
    */
  def withReachableNode(node: UniqueAddress): WorldView = changeReachability(node, Reachable)

  /**
    * Change the `node`'s status to `Unreachable`.
    */
  def withUnreachableNode(node: UniqueAddress): WorldView = changeReachability(node, Unreachable)

  /**
    * Change the `node`'s status to `IndirectlyConnected`.
    */
  def withIndirectlyConnectedNode(node: UniqueAddress): WorldView =
    changeReachability(node, IndirectlyConnected)

  /**
    * Replace the `selfMember` with `member`.
    *
    * Used in tests.
    */
  private[sbr] def changeSelf(member: Member): WorldView =
    if (member.uniqueAddress === selfUniqueAddress) this
    else {
      val newSelfStatus = otherMembersStatus
        .getOrElse(member.uniqueAddress, Status(member, Reachable))
        .withReachability(Reachable)

      selfStatus.member.status match {
        case Removed =>
          copy(
            selfUniqueAddress = member.uniqueAddress,
            selfStatus = newSelfStatus,
            otherMembersStatus = otherMembersStatus - member.uniqueAddress
          )

        case _ =>
          copy(
            selfUniqueAddress = member.uniqueAddress,
            selfStatus = newSelfStatus,
            otherMembersStatus = otherMembersStatus - member.uniqueAddress + (selfUniqueAddress -> selfStatus)
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
    * True when `node` needs to be considered in split-brain resolution strategies. Otherwise, false.
    */
  private def shouldBeConsidered(node: Node): Boolean =
    node.member.status != Joining && node.member.status != WeaklyUp
//
//  match {
//    case UnreachableNode(member) => member.status != Joining && member.status != WeaklyUp
//    case ReachableNode(member)   => member.status != Joining && member.status != WeaklyUp
//
//    // When indirectly connected nodes are tracked they do not
//    // appear in the considered nodes as they will be downed
//    // in parallel.
//    case _: IndirectlyConnectedNode => false
//  }

  lazy val simple: SimpleWorldView = SimpleWorldView(
    selfUniqueAddress,
    reachableNodes.toList.map(n => SimpleMember.fromMember(n.member)),
    indirectlyConnectedNodes.toList.map(n => SimpleMember.fromMember(n.member)),
    unreachableNodes.toList.map(n => SimpleMember.fromMember(n.member))
  )
}

private[sbr] object WorldView {
  final case class SimpleWorldView(
      selfUniqueAddress: UniqueAddress,
      reachableMembers: List[SimpleMember],
      indirectlyConnectedMembers: List[SimpleMember],
      unreachableMembers: List[SimpleMember]
  )

  object SimpleWorldView {
    implicit val simpleWorldViewEncoder: Encoder[SimpleWorldView] = deriveEncoder
    implicit val simpleWorldEq: Eq[SimpleWorldView] = Eq.and(
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
    new WorldView(selfMember.uniqueAddress, Status(selfMember, Reachable), Map.empty)

  /**
    * Create a world view based on the `state`.
    */
  def fromSnapshot(selfMember: Member, snapshot: CurrentClusterState): WorldView = {
    val latestSelfMember = snapshot.members.find(_.uniqueAddress === selfMember.uniqueAddress)
    val otherMembers = latestSelfMember.fold(snapshot.members)(snapshot.members - _)

    // Initiate the world view with the current
    // cluster member. The snapshot contains its
    // latest state. If not, use the provided the
    // one, which should be a placeholder where
    // it resides in the removed state.
    val w = latestSelfMember.fold(WorldView.init(selfMember))(WorldView.init)

    // add reachable members to the world view
    val w1 = (otherMembers -- snapshot.unreachable).foldLeft(w) {
      case (w, member) =>
        member.status match {
          case Removed => w.removeMember(member)
          case _       => w.addOrUpdate(member).withReachableNode(member.uniqueAddress)
        }
    }

    // add unreachable members to the world view
    snapshot.unreachable // selfMember cannot be unreachable
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
  def fromNodes(selfNode: Node, otherNodes: Set[Node]): WorldView = {
    def convert(node: Node): (UniqueAddress, Status) =
      node.member.uniqueAddress -> (node match {
        case _: UnreachableNode         => Status(node.member, Unreachable)
        case _: ReachableNode           => Status(node.member, Reachable)
        case _: IndirectlyConnectedNode => Status(node.member, IndirectlyConnected)
      })

    assert(!otherNodes.contains(selfNode))

    val (selfUniqueAddress, selfStatus) = convert(selfNode)

    val (_, others) = otherNodes.partition(_.member.status === Removed)

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
      case Reachable           => ReachableNode(member)
      case Unreachable         => UnreachableNode(member)
      case IndirectlyConnected => IndirectlyConnectedNode(member)
    }

  final case class Status(member: Member, reachability: SBReachabilityStatus) {
    def withReachability(updatedReachability: SBReachabilityStatus): Status =
      copy(reachability = updatedReachability)
    def withMember(updatedMember: Member): Status = copy(member = updatedMember)
  }
}
