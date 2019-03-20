package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.Member
import akka.cluster.sbr.{Reachable, Staged, Unreachable, WorldView}
import cats.implicits._

sealed abstract class KeepOldestView extends Product with Serializable

object KeepOldestView {
  def apply(worldView: WorldView, downIfAlone: Boolean, role: String): Either[KeepOldestViewError, KeepOldestView] = {
    val allNodesSortedByAge = worldView.allConsideredNodesWithRole(role).toList.sorted(Member.ageOrdering)

    for {
      oldestNode <- allNodesSortedByAge.headOption
//      _ = println(allNodesSortedByAge)
//      _ = println(s"OLDEST: $oldestNode")
      reachability <- worldView.statusOf(oldestNode)
//      _ = println(s"REACHABILITY: $reachability")
    } yield
      reachability match {
        case Reachable =>
          if (!downIfAlone || worldView.reachableConsideredNodes.size > 1) OldestReachable.asRight
          else OldestAlone.asRight
        case Unreachable => OldestUnreachable.asRight
        case Staged      => OldestIsStaged(oldestNode).asLeft
      }
  }.fold[Either[KeepOldestViewError, KeepOldestView]](NoOldestNode.asLeft)(identity)

  sealed abstract class KeepOldestViewError(message: String) extends Throwable(message)
  final case object NoOldestNode                             extends KeepOldestViewError("")
  final case class OldestIsStaged(member: Member)            extends KeepOldestViewError(s"$member")
}

final case object OldestReachable   extends KeepOldestView
final case object OldestAlone       extends KeepOldestView
final case object OldestUnreachable extends KeepOldestView
