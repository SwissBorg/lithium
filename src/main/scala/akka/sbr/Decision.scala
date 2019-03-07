package akka.sbr

import akka.actor.Address
import monocle.Getter

sealed abstract class Decision

object Decision {
  def staticQuorum(quorum: ReachableNodeGroup, unreachableQuorum: UnreachableNodeGroup): Decision =
    (quorum, unreachableQuorum) match {

      /**
       * If we decide DownReachable the entire cluster will shutdown. Always?
       * If we decide DownUnreachable we might create a SB if there's actually quorum in the unreachable
       */
      case (_: ReachableQuorum, _: UnreachablePotentialQuorum) => ???

      case (_: ReachableQuorum, subQuorum: UnreachableSubQuorum) => new DownUnreachable(subQuorum) {}
      case (_: ReachableQuorum, _: EmptyUnreachable)             => new Idle() {}

      /**
       * Potentially shuts down the cluster if there's
       * no quorum on the other side of the split.
       */
      case (subQuorum: ReachableSubQuorum, _: UnreachablePotentialQuorum) => new DownReachable(subQuorum) {}

      /**
       * The cluster will shutdown.
       */
      case (subQuorum: ReachableSubQuorum, _: UnreachableSubQuorum) => new DownReachable(subQuorum) {}

      /**
       * The cluster will shutdown.
       * Happens when to many nodes crash at the same time.
       *
       * TODO: If we do nothing at the next unreachable event the cluster will shutdown. Down now or later?
       */
      case (subQuorum: ReachableSubQuorum, _: EmptyUnreachable) => new DownReachable(subQuorum) {}
    }

  val addressesToDown: Getter[Decision, Set[Address]] = Getter[Decision, Set[Address]] {
    case DownReachable(nodeGroup)   => nodeGroup.nodes.toSortedSet.map(_.address)
    case DownUnreachable(nodeGroup) => nodeGroup.nodes.toSortedSet.map(_.address)
    case _: Idle                    => Set.empty
  }

  implicit class DecisionOps(private val decision: Decision) extends AnyVal {
    def addressesToDown: Set[Address] = Decision.addressesToDown.get(decision)
  }
}

/**
 * The reachable nodes should be downed.
 */
sealed abstract case class DownReachable(nodeGroup: ReachableSubQuorum) extends Decision

/**
 * The unreachable nodes should be downed.
 */
sealed abstract case class DownUnreachable(nodeGroup: UnreachableSubQuorum) extends Decision

/**
 * Nothing has to be done. The cluster is in a
 * correct state.
 */
sealed abstract case class Idle() extends Decision
