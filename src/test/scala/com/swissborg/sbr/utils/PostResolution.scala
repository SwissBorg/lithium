package com.swissborg.sbr.utils

import cats.Monoid
import com.swissborg.sbr._
import com.swissborg.sbr.strategy.StrategyDecision
import com.swissborg.sbr.strategy.StrategyDecision._

import scala.collection.SortedSet

/**
  * Represents the cluster after applying a strategy's decision.
  */
final case class PostResolution(
    /**
      * The nodes in each partition.
      */
    partitions: List[SortedSet[Node]]
) {

  /**
    * True if there are no unreachable nodes in the cluster and
    * that all the partitions have the same elements, aka the
    * resolution did create multiple clusters.
    */
  lazy val noSplitBrain: Boolean = {
    val nonEmptyPartitions = partitions.filter(_.nonEmpty)

    if (nonEmptyPartitions.lengthCompare(1) > 0) {
      // At least 2 non-empty partitions
      nonEmptyPartitions.tail
        .foldLeft((true, nonEmptyPartitions.head)) {
          case ((noSplitBrain, expectedPartition), partition) =>
            val hasSameNodes = expectedPartition.sameElements(partition)

            val allReachableNodes = partition.forall {
              case _: ReachableNode => true
              case _                => false
            }

            (noSplitBrain && hasSameNodes && allReachableNodes, expectedPartition)
        }
        ._1
    } else {
      // 1 or less non-empty partitions
      true
    }
  }
}

object PostResolution {
  val empty: PostResolution = PostResolution(List.empty)

  def one(nodes: SortedSet[Node]): PostResolution = PostResolution(List(nodes))

  def fromDecision(worldView: WorldView)(decision: StrategyDecision): PostResolution =
    decision match {
      // In all these cases the entire partition will down itself.
      case _: DownReachable               => PostResolution.empty
      case DownThese(_: DownReachable, _) => PostResolution.empty
      case DownThese(_, _: DownReachable) => PostResolution.empty

      case _ =>
        val nodesAfterDowning = decision.nodesToDown.iterator
          .foldLeft(worldView) {
            case (worldView, nodeToDown) => worldView.removeMember(nodeToDown.member)
          }
          .nodes
          .toSortedSet

        PostResolution.one(nodesAfterDowning)
    }

  implicit val remainingPartitionsMonoid: Monoid[PostResolution] = new Monoid[PostResolution] {
    override def empty: PostResolution = PostResolution.empty

    override def combine(x: PostResolution, y: PostResolution): PostResolution =
      PostResolution(x.partitions ++ y.partitions)
  }
}
