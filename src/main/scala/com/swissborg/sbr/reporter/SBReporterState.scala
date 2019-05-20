package com.swissborg.sbr.reporter

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.Member
import com.swissborg.sbr.reporter.SBReporterState.ChangeQueue
import com.swissborg.sbr.{WorldView, reporter}

import scala.collection.immutable.Queue

/**
 * State of the [[SBReporter]].
 *
 * @param worldView the view of the cluster from the current cluster node.
 * @param changeQueue queue accumulating membership state changed.
 */
final case class SBReporterState(worldView: WorldView, changeQueue: ChangeQueue) {
  import SBReporterState._

  /**
   * Update the world view with the changes described by the change-queue after
   * finalizing it with `seenBy`.
   */
  def consumeQueue(seenBy: Set[Address]): SBReporterState =
    copy(
      worldView = changeQueue match {
        case Empty => worldView.withAllSeenBy(seenBy)
        case AwaitingEvents(events) =>
          events.foldLeft(worldView) {
            case (w, event) =>
              event match {
                case MemberJoined(member)     => w.updateMember(member, seenBy)
                case MemberWeaklyUp(member)   => w.updateMember(member, seenBy)
                case MemberUp(member)         => w.updateMember(member, seenBy)
                case MemberLeft(member)       => w.updateMember(member, seenBy)
                case MemberExited(member)     => w.updateMember(member, seenBy)
                case MemberDowned(member)     => w.updateMember(member, seenBy)
                case MemberRemoved(member, _) => w.removeMember(member, seenBy)
              }
          }

        case _ => worldView
      },
      changeQueue = Empty
    )

  lazy val pruneRemoved: SBReporterState = copy(worldView = worldView.pruneRemoved)

  def enqueue(e: MemberEvent): SBReporterState =
    copy(changeQueue = changeQueue match {
      case Empty                  => AwaitingEvents(Queue(e))
      case AwaitingEvents(events) => AwaitingEvents(events.enqueue(e))
      case _                      => changeQueue
    })

  /**
   * Set the member as reachable.
   */
  def withReachableMember(m: Member): SBReporterState =
    copy(worldView = worldView.withReachableMember(m))

  /**
   * Set the member as unreachable.
   */
  def withUnreachableMember(m: Member): SBReporterState =
    copy(worldView = worldView.withUnreachableMember(m))

  /**
   * Set the member as indirectly connected.
   */
  def withIndirectlyConnectedMember(m: Member): SBReporterState =
    copy(worldView = worldView.withIndirectlyConnectedMember(m))
}

object SBReporterState {
  def fromSnapshot(selfMember: Member, snapshot: CurrentClusterState): SBReporterState =
    reporter.SBReporterState(WorldView.fromSnapshot(selfMember, snapshot), Empty)

  /**
   * Queue accumulating the membership changes.
   *
   * Assumes that all the member events are received before the seen-by event. In other words,
   * changes are separated by seen-by events.
   */
  sealed abstract class ChangeQueue

  case object Empty extends ChangeQueue

  /**
   * A partial change queue. Still waiting for a seen-by event completing the queue.
   */
  final case class AwaitingEvents(events: Queue[MemberEvent]) extends ChangeQueue

  /**
   * No member events have been received for this window.
   * The queue is complete at this point.
   */
  final case class Eventless(seenBy: Set[Address]) extends ChangeQueue

  /**
   * Member events and a seen-by event.
   * The queue is complete at this point.
   */
  final case class Complete(events: Queue[MemberEvent], seenBy: Set[Address]) extends ChangeQueue
}
