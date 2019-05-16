package com.swissborg.sbr.utils

import akka.cluster.Member
import cats.Monoid
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.implicits._

import scala.collection.SortedSet

/**
 * Represents the partitions after running the decision.
 */
final case class PostResolution(partitions: List[SortedSet[Member]]) {

  /**
   * True if there are not enough partitions for a split-brain
   * or that all the partitions have the same members.
   */
  lazy val noSplitBrain: Boolean = {
    val nonEmptyPartitions = partitions.filter(_.nonEmpty)
    nonEmptyPartitions.size <= 1 || nonEmptyPartitions.tail
      .foldLeft((true, nonEmptyPartitions.head)) {
        case ((b, expectedPartition), partition) => (expectedPartition.sameElements(partition) && b, expectedPartition)
      }
      ._1
  }
}

object PostResolution {
  def fromDecision(worldView: WorldView)(decision: StrategyDecision): PostResolution =
    decision match {
      case DownReachable(_) => PostResolution(List(SortedSet.empty))
      case DownSelf(_)      => PostResolution(List(SortedSet.empty))

      case DownThese(decision1, decision2) =>
        val v = fromDecision(worldView)(decision1) |+| fromDecision(worldView)(decision2)
        v.copy(partitions = List(v.partitions.head.union(v.partitions.last)))

      case decision =>
        PostResolution(List(worldView.nodes.map(_.member).toSortedSet -- decision.nodesToDown.map(_.member)))
    }

  implicit val remainingPartitionsMonoid: Monoid[PostResolution] = new Monoid[PostResolution] {
    override def empty: PostResolution = PostResolution(List.empty)

    override def combine(x: PostResolution, y: PostResolution): PostResolution =
      PostResolution(x.partitions ++ y.partitions)
  }
}
