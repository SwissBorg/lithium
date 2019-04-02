package akka.cluster.sbr

import akka.actor.Actor.Receive
import akka.actor.{ActorContext, ActorRef, Cancellable}
import akka.cluster.ClusterEvent.{MemberEvent, ReachabilityEvent}
import cats.implicits._

import scala.concurrent.ExecutionContext

final case class UnreachableNodesState(worldView: WorldView,
                                       stabilityTrigger: Cancellable,
                                       instabilityTrigger: Cancellable,
                                       snitches: Snitches) {
  import UnreachableNodesState._

  def onMemberEvent(e: MemberEvent)(onMemberEvent: OnMemberEvent): Receive = {
    import onMemberEvent._

    worldView
      .memberEvent(e)
      .map { w =>
        f(copy(worldView = w, stabilityTrigger = resetStability(stabilityTrigger)))
      }
      .toTry
      .get
  }

  def onReachabilityEvent(e: ReachabilityEvent)(
    onNoUnreachableNodes: => OnNoUnreachableNodes,
    onUnreachableNodes: => OnUnreachableNodes
  )(implicit context: ActorContext, ec: ExecutionContext, self: ActorRef): Receive =
    worldView
      .reachabilityEvent(e)
      .map { w =>
        if (w.unreachableNodes.isEmpty) {
          val onNoUnreachableNodes0 = onNoUnreachableNodes
          import onNoUnreachableNodes0._

          disableInstability(instabilityTrigger)

          f(NoUnreachableNodesState(w, snitches.snitch(e)))
        } else {
          val onUnreachableNodes0 = onUnreachableNodes
          import onUnreachableNodes0._

          f(
            copy(worldView = w,
                 stabilityTrigger = if (worldView === w) stabilityTrigger else resetStability(stabilityTrigger),
                 snitches = snitches.snitch(e))
          )
        }
      }
      .toTry
      .get
}

object UnreachableNodesState {
  final case class OnMemberEvent(f: UnreachableNodesState => Receive, resetStability: Cancellable => Cancellable)

  final case class OnNoUnreachableNodes(f: NoUnreachableNodesState => Receive, disableInstability: Cancellable => Unit)

  final case class OnUnreachableNodes(f: UnreachableNodesState => Receive, resetStability: Cancellable => Cancellable)
}
