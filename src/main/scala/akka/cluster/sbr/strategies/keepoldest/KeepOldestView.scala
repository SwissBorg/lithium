package akka.cluster.sbr.strategies.keepoldest

import akka.cluster.Member
import akka.cluster.sbr.{Reachable, Unreachable, WorldView}
import akka.cluster.sbr.strategies.keepoldest.KeepOldest.Config
import cats.implicits._

import scala.collection.immutable.SortedSet

sealed abstract class KeepOldestView extends Product with Serializable

object KeepOldestView {
  def apply(worldView: WorldView, config: Config): Either[Error.type, KeepOldestView] = {
    val allNodesSortedByAge = SortedSet(worldView.allNodes.toSeq: _*)(Member.ageOrdering)

    val maybeKeepOldestView = for {
      oldestNode <- allNodesSortedByAge.headOption
      reachability <- worldView.reachabilityOf(oldestNode)
    } yield
      reachability match {
        case Reachable =>
          if (worldView.reachableNodes.size > 1) OldestReachable
          else OldestAlone
        case Unreachable => OldestUnreachable
      }

    maybeKeepOldestView.fold[Either[Error.type, KeepOldestView]](Error.asLeft)(_.asRight)
  }
}

final case object OldestReachable extends KeepOldestView
final case object OldestAlone extends KeepOldestView
final case object OldestUnreachable extends KeepOldestView

final case object Error extends Throwable
