package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.sbr.SBReporterState.ChangeQueue

import scala.collection.immutable.Queue

final case class SBReporterState(worldView: WorldView, changeQueue: ChangeQueue) {
  import SBReporterState._

  def flush(seenBy: Set[Address]): SBReporterState =
    copy(
      worldView = changeQueue match {
        case Empty => worldView.allSeenBy(seenBy)
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
                case MemberRemoved(member, _) => w.memberRemoved(member, seenBy)
              }
          }

        case _ => worldView
      },
      changeQueue = Empty
    )

  def enqueue(e: MemberEvent): SBReporterState =
    copy(changeQueue = changeQueue match {
      case Empty                  => AwaitingEvents(Queue(e))
      case AwaitingEvents(events) => AwaitingEvents(events.enqueue(e))
      case _                      => changeQueue
    })

  def reachableMember(m: Member): SBReporterState     = copy(worldView = worldView.reachableMember(m))
  def unreachableMember(m: Member): SBReporterState   = copy(worldView = worldView.unreachableMember(m))
  def indirectlyConnected(m: Member): SBReporterState = copy(worldView = worldView.indirectlyConnectedMember(m))
}

object SBReporterState {
  def apply(selfMember: Member): SBReporterState =
    SBReporterState(WorldView.init(selfMember), Empty)

  def fromSnapshot(s: CurrentClusterState, selfMember: Member): SBReporterState =
    SBReporterState(WorldView.fromSnapshot(selfMember, s), Empty)

  sealed abstract class ChangeQueue
  final case object Empty                                                     extends ChangeQueue
  final case class AwaitingEvents(events: Queue[MemberEvent])                 extends ChangeQueue
  final case class Eventless(seenBy: Set[Address])                            extends ChangeQueue
  final case class Complete(events: Queue[MemberEvent], seenBy: Set[Address]) extends ChangeQueue
}
