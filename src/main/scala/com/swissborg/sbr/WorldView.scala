package com.swissborg.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus.{Joining, Removed, WeaklyUp}
import akka.cluster.{Member, UniqueAddress}
import cats.data.NonEmptySet
import cats.implicits._
import com.swissborg.sbr.WorldView.Status
import com.swissborg.sbr.failuredetector.SBFailureDetector._
import com.swissborg.sbr.implicits._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

/**
 * Represents the view of the cluster from the point of view of the
 * `selfNode`.
 */
final case class WorldView private (
  private[sbr] val selfUniqueAddress: UniqueAddress,
  private[sbr] val selfStatus: Status,
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
  private[sbr] val otherMembersStatus: Map[UniqueAddress, Status],
  /**
   * Removed members are kept as the information
   * is useful to detect the case when the removal
   * might not have been seen by a partition.
   */
  private[sbr] val removedMembersSeenBy: Map[UniqueAddress, Set[Address]]
) {
  import WorldView._

  assert(!otherMembersStatus.contains(selfUniqueAddress), s"$otherMembersStatus <- $selfUniqueAddress")

  lazy val selfNode: Node = toNode(selfStatus.member, selfStatus.reachability)

  /**
   * All the nodes in the cluster.
   */
  lazy val nodes: NonEmptySet[Node] = {
    val otherNodes: Seq[Node] = otherMembersStatus.values.map {
      case Status(member, reachability, _) => toNode(member, reachability)
    }(collection.breakOut)

    NonEmptySet.of(selfNode, otherNodes: _*)
  }

  lazy val members: NonEmptySet[Member] = nodes.map(_.member)

  /**
   * The nodes that need to be considered in split-brain resolutions.
   *
   * A node is to be considered when it isn't in the "Joining" or "WeaklyUp"
   * states. These status are ignored since a node can join and become
   * weakly-up during a network-partition.
   */
  lazy val consideredNodes: Set[CleanNode] = nodes.collect {
    case node: CleanNode if shouldBeConsidered(node) => node
  }

  /**
   * The nodes with the given role, that need to be considered in
   * split-brain resolutions.
   */
  def consideredNodesWithRole(role: String): Set[CleanNode] =
    if (role.nonEmpty) consideredNodes.filter(_.member.roles.contains(role)) else consideredNodes

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
    if (role.nonEmpty) consideredReachableNodes.filter(_.member.roles.contains(role)) else consideredReachableNodes

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
    if (role.nonEmpty) indirectlyConnectedNodes.filter(_.member.roles.contains(role)) else indirectlyConnectedNodes

  /**
   * All the indirectly connected nodes.
   */
  lazy val indirectlyConnectedNodes: Set[IndirectlyConnectedNode] = nodes.collect {
    case r: IndirectlyConnectedNode => r
  }

  lazy val removedMembers: Set[UniqueAddress] = removedMembersSeenBy.keySet

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
    if (role.nonEmpty) consideredUnreachableNodes.filter(_.member.roles.contains(role)) else consideredUnreachableNodes

  def updateMember(member: Member, seenBy: Set[Address]): WorldView =
    if (member.uniqueAddress === selfUniqueAddress) {
      copy(selfStatus = selfStatus.withMember(member).withSeenBy(seenBy))
    } else {
      otherMembersStatus
        .get(member.uniqueAddress)
        .fold(
          // Assumes the member is reachable if seen for the 1st time.
          copy(otherMembersStatus = otherMembersStatus + (member.uniqueAddress -> Status(member, Reachable, seenBy)))
        )(
          s =>
            copy(
              otherMembersStatus = otherMembersStatus - member.uniqueAddress + (member.uniqueAddress -> s
                .withMember(member)
                .withSeenBy(seenBy))
          )
        )
    }

  def removeMember(member: Member, seenBy: Set[Address]): WorldView =
    if (member.uniqueAddress === selfUniqueAddress) {
      copy(member.uniqueAddress, selfStatus = selfStatus.withMember(member).withSeenBy(seenBy)) // ignore only update // todo is it safe?
    } else {
      otherMembersStatus
        .get(member.uniqueAddress)
        .fold(copy(removedMembersSeenBy = removedMembersSeenBy + (member.uniqueAddress -> seenBy))) { _ =>
          copy(otherMembersStatus = otherMembersStatus - member.uniqueAddress,
               removedMembersSeenBy = removedMembersSeenBy + (member.uniqueAddress -> seenBy))
        }
    }

  /**
   * Change the `node`'s state to `Reachable`.
   */
  def withReachableMember(member: Member): WorldView = changeReachability(member, Reachable)

  /**
   * Change the `node`'s status to `Unreachable`.
   */
  def withUnreachableMember(member: Member): WorldView = changeReachability(member, Unreachable)

  /**
   * Change the `node`'s status to `IndirectlyConnected`.
   */
  def withIndirectlyConnectedMember(member: Member): WorldView = changeReachability(member, IndirectlyConnected)

  /**
   * Set every member's seen-by to `seenBy`.
   */
  def withAllSeenBy(seenBy: Set[Address]): WorldView =
    copy(
      selfStatus = selfStatus.withSeenBy(seenBy),
      otherMembersStatus = otherMembersStatus.mapValues(s => s.withSeenBy(seenBy)),
      removedMembersSeenBy = removedMembersSeenBy.mapValues(_ => seenBy)
    )

  /**
   * The nodes that have seen the `member` in its current state in the world view.
   */
  def seenBy(member: Member): Set[Address] =
    if (member.uniqueAddress === selfUniqueAddress) selfStatus.seenBy
    else
      otherMembersStatus
        .get(member.uniqueAddress)
        .fold(removedMembersSeenBy.getOrElse(member.uniqueAddress, Set.empty))(_.seenBy)

  lazy val pruneRemoved: WorldView = copy(removedMembersSeenBy = Map.empty)

  /**
   * Replace the `selfMember` with `member`.
   *
   * Used in tests.
   */
  private[sbr] def changeSelf(member: Member): WorldView =
    if (member.uniqueAddress === selfUniqueAddress) this
    else {
      val newSelfStatus = otherMembersStatus
        .getOrElse(member.uniqueAddress, Status(member, Reachable, Set.empty))
        .withReachability(Reachable)

      selfStatus.member.status match {
        case Removed =>
          copy(
            selfUniqueAddress = member.uniqueAddress,
            selfStatus = newSelfStatus,
            otherMembersStatus = otherMembersStatus - member.uniqueAddress,
            removedMembersSeenBy = removedMembersSeenBy - member.uniqueAddress + (selfUniqueAddress -> selfStatus.seenBy)
          )

        case _ =>
          copy(
            selfUniqueAddress = member.uniqueAddress,
            selfStatus = newSelfStatus,
            otherMembersStatus = otherMembersStatus - member.uniqueAddress + (selfUniqueAddress -> selfStatus),
            removedMembersSeenBy = removedMembersSeenBy - member.uniqueAddress
          )
      }
    }

  /**
   * Change the reachability of `member` with `reachability`.
   */
  private def changeReachability(member: Member, reachability: SBRReachabilityStatus): WorldView =
    if (member.uniqueAddress === selfUniqueAddress) {
      copy(selfUniqueAddress, selfStatus = selfStatus.withReachability(reachability))
    } else {
      otherMembersStatus
        .get(member.uniqueAddress)
        .fold(
          copy(
            otherMembersStatus = otherMembersStatus + (member.uniqueAddress -> Status(member, reachability, Set.empty))
          )
        ) { s =>
          copy(
            otherMembersStatus = otherMembersStatus - member.uniqueAddress + (member.uniqueAddress -> s
              .withReachability(reachability)) // todo update member?
          )
        }
    }

  /**
   * True when `node` needs to be considered in split-brain resolution strategies. Otherwise, false.
   */
  private def shouldBeConsidered(node: Node): Boolean = node match {
    case UnreachableNode(member) => member.status != Joining && member.status != WeaklyUp
    case ReachableNode(member)   => member.status != Joining && member.status != WeaklyUp

    // When indirectly connected nodes are tracked they do not
    // appear in the considered nodes as they will be downed
    // in parallel.
    case _: IndirectlyConnectedNode => false
  }

  lazy val simple: SimpleWorldView = SimpleWorldView(
    selfUniqueAddress,
    reachableNodes.toList.map(n => SimpleMember.fromMember(n.member)),
    indirectlyConnectedNodes.toList.map(n => SimpleMember.fromMember(n.member)),
    unreachableNodes.toList.map(n => SimpleMember.fromMember(n.member))
  )
}

object WorldView {
  final case class SimpleWorldView(
    selfUniqueAddress: UniqueAddress,
    reachableMembers: List[SimpleMember],
    indirectlyConnectedMembers: List[SimpleMember],
    unreachableMembers: List[SimpleMember],
  )

  object SimpleWorldView {
    implicit val simpleWorldViewEncoder: Encoder[SimpleWorldView] = deriveEncoder
  }

  /**
   * Create an empty world view.
   */
  def init(selfMember: Member): WorldView =
    new WorldView(selfMember.uniqueAddress,
                  Status(selfMember, Reachable, Set(selfMember.address)),
                  Map.empty,
                  Map.empty)

  /**
   * Create a world view based on the `state`.
   */
  def fromSnapshot(selfMember: Member, snapshot: CurrentClusterState): WorldView = {
    val latestSelfMember = snapshot.members.find(_.uniqueAddress === selfMember.uniqueAddress)
    val otherMembers     = latestSelfMember.fold(snapshot.members)(snapshot.members - _)

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
          case Removed => w.withReachableMember(member).removeMember(member, Set.empty)
          case _       => w.withReachableMember(member)
        }
    }

    // add unreachable members to the world view
    snapshot.unreachable // selfMember cannot be unreachable
      .foldLeft(w1) {
        case (w, member) =>
          member.status match {
            case Removed => w.withUnreachableMember(member).removeMember(member, Set.empty)
            case _       => w.withUnreachableMember(member)
          }
      }
      .withAllSeenBy(snapshot.seenBy)
  }

  /**
   * Build a world view based on the given nodes.
   *
   * Used in tests.
   */
  def fromNodes(selfNode: Node, seenBy: Set[Address], otherNodesSeenBy: Map[Node, Set[Address]]): WorldView = {
    def convert(node: Node, seenBy: Set[Address]): (UniqueAddress, Status) =
      node.member.uniqueAddress -> (node match {
        case _: UnreachableNode         => Status(node.member, Unreachable, seenBy)
        case _: ReachableNode           => Status(node.member, Reachable, seenBy)
        case _: IndirectlyConnectedNode => Status(node.member, IndirectlyConnected, seenBy)
      })

    assert(!otherNodesSeenBy.contains(selfNode))

    val (selfUniqueAddress, selfStatus) = convert(selfNode, seenBy)

    val (removed, others) = otherNodesSeenBy.partition(_._1.member.status === Removed)

    val convertF = (convert _).tupled

    WorldView(
      selfUniqueAddress,
      selfStatus,
      others.map(convertF),
      removed.map(convertF).mapValues(_.seenBy)
    )
  }

  /**
   * Convert the `member` and its `reachability` to a [[Node]].
   */
  private def toNode(member: Member, reachability: SBRReachabilityStatus): Node =
    reachability match {
      case Reachable           => ReachableNode(member)
      case Unreachable         => UnreachableNode(member)
      case IndirectlyConnected => IndirectlyConnectedNode(member)
    }

  final case class Status(member: Member, reachability: SBRReachabilityStatus, seenBy: Set[Address]) {
    def withSeenBy(updatedSeenBy: Set[Address]): Status                      = copy(seenBy = updatedSeenBy)
    def withReachability(updatedReachability: SBRReachabilityStatus): Status = copy(reachability = updatedReachability)
    def withMember(updatedMember: Member): Status                            = copy(member = updatedMember)
  }
}