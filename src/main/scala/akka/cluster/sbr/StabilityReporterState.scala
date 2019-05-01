package akka.cluster.sbr

import StabilityReporterState.ChangeQueue
import akka.actor.Address
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberEvent}
import akka.cluster.Member

import scala.collection.immutable.Queue

final case class StabilityReporterState(worldView: WorldView, changeQueue: ChangeQueue) {
  import StabilityReporterState._

  def flush(seenBy: Set[Address]): StabilityReporterState =
    copy(
      worldView = changeQueue match {
        case Empty                  => worldView.seenBy(seenBy)
        case AwaitingEvents(events) => events.foldLeft(worldView)(_.memberEvent(_, seenBy))
        case _                      => worldView
      },
      changeQueue = Empty
    )

  def enqueue(e: MemberEvent): StabilityReporterState =
    copy(changeQueue = changeQueue match {
      case Empty                  => AwaitingEvents(Queue(e))
      case AwaitingEvents(events) => AwaitingEvents(events.enqueue(e))
      case _                      => changeQueue
    })

  def reachableMember(m: Member): StabilityReporterState     = copy(worldView = worldView.reachableMember(m))
  def unreachableMember(m: Member): StabilityReporterState   = copy(worldView = worldView.unreachableMember(m))
  def indirectlyConnected(m: Member): StabilityReporterState = copy(worldView = worldView.indirectlyConnectedMember(m))
}

object StabilityReporterState {
  def apply(selfMember: Member): StabilityReporterState =
    StabilityReporterState(WorldView.init(selfMember, trackIndirectlyConnected = true), Empty)

  def fromSnapshot(s: CurrentClusterState, selfMember: Member): StabilityReporterState =
    StabilityReporterState(WorldView.fromSnapshot(selfMember, trackIndirectlyConnected = true, s), Empty)

  sealed abstract class ChangeQueue
  final case object Empty                                                     extends ChangeQueue
  final case class AwaitingEvents(events: Queue[MemberEvent])                 extends ChangeQueue
  final case class Eventless(seenBy: Set[Address])                            extends ChangeQueue
  final case class Complete(events: Queue[MemberEvent], seenBy: Set[Address]) extends ChangeQueue
}
