package akka.cluster.sbr

import akka.actor.Actor.Receive
import akka.actor.{ActorContext, ActorRef, Cancellable}
import akka.cluster.ClusterEvent.{MemberEvent, ReachabilityEvent, ReachableMember, UnreachableMember}
import akka.cluster.sbr.Downer.Snitch

import scala.concurrent.ExecutionContext

final case class NoUnreachableNodesState(worldView: WorldView, secondGuesses: Snitches) {
  import NoUnreachableNodesState._

  def onMemberEvent(e: MemberEvent)(f: NoUnreachableNodesState => Receive): Receive =
    worldView.memberEvent(e).map(w => f(copy(worldView = w))).toTry.get

  def onReachabilityEvent(e: ReachabilityEvent)(
    onUnreachable: => OnUnreachable,
    onReachable: => OnReachable
  )(implicit context: ActorContext, ec: ExecutionContext, self: ActorRef): Receive =
    worldView
      .reachabilityEvent(e)
      .map { worldView =>
        e match {
          case UnreachableMember(member) =>
            val onUnreachable0 = onUnreachable // run once
            import onUnreachable0._

            f(
              UnreachableNodesState(worldView, scheduleStability(), scheduleInstability(), Snitches(publish).snitch(e))
            )

          case ReachableMember(member) =>
            val onReachable0 = onReachable
            import onReachable0._

            f(NoUnreachableNodesState(worldView, secondGuesses.snitch(e)))
        }
      }
      .toTry
      .get
}

object NoUnreachableNodesState {
  final case class OnUnreachable(f: UnreachableNodesState => Receive,
                                 scheduleStability: () => Cancellable,
                                 scheduleInstability: () => Cancellable,
                                 publish: (String, Snitch) => Unit)

  final case class OnReachable(f: NoUnreachableNodesState => Receive)
}
