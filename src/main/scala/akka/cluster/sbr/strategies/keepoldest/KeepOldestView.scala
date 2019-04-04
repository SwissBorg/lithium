package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.Member
import akka.cluster.sbr._
import cats.implicits._

sealed abstract class KeepOldestView extends Product with Serializable

object KeepOldestView {
  def apply(worldView: WorldView, downIfAlone: Boolean, role: String): Either[NoOldestNode.type, KeepOldestView] = {
    val allNodesSortedByAge = worldView.consideredNodesWithRole(role).toList.sortBy(_.member)(Member.ageOrdering)

    allNodesSortedByAge.headOption.fold[Either[NoOldestNode.type, KeepOldestView]](NoOldestNode.asLeft) {
      case _: ReachableNode =>
        if (!downIfAlone || worldView.consideredReachableNodes.size > 1) OldestReachable.asRight
        else OldestAlone.asRight
      case _: UnreachableNode => OldestUnreachable.asRight
    }
  }

  final case object NoOldestNode extends Throwable
}

final case object OldestReachable   extends KeepOldestView
final case object OldestAlone       extends KeepOldestView
final case object OldestUnreachable extends KeepOldestView
