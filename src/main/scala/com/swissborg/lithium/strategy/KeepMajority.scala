package com.swissborg.lithium

package strategy

import akka.cluster.MemberStatus._
import cats.ApplicativeError
import cats.implicits._
import com.swissborg.lithium.implicits._

/**
 * Split-brain resolver strategy that will keep the partition containing more than half of the nodes and down the other
 * ones. In case of an even number and nodes and none is a majority the partition containing the node
 * if the lowest address will be picked as a survivor.
 *
 * This strategy is useful when the cluster size is dynamic.
 */
private[lithium] class KeepMajority[F[_]: ApplicativeError[*[_], Throwable]](config: KeepMajority.Config,
                                                                             weaklyUpMembersAllowed: Boolean)
    extends Strategy[F] {

  import config._

  override def takeDecision(worldView: WorldView): F[Decision] = {
    val reachableConsideredNodes =
      worldView.reachableNodesWithRole(role).filter(n => n.status === Up || n.status === Leaving)

    // Nodes can change their status at the same time as a partition. This is especially problematic for
    // joining/weakly-up members becoming up. The side that sees the new up nodes might think it has a majority of
    // nodes. To counter this, the unreachable nodes (the other side) that are joining or weakly-up are assumed to have
    // become up. If it's really the case, this will prevent both sides from downing each other and creating a
    // split-brain. On the other, if the other side didn't see these nodes as up both side might down themselves and
    // down the entire cluster. Better be safe than sorry.
    val unreachableConsideredNodes =
      worldView
        .unreachableNodesWithRole(role)
        .filter { n =>
          // Assume unreachable joining/weakly-up nodes are seen as up on the other side
          val isJoining =
            if (weaklyUpMembersAllowed) {
              n.status === WeaklyUp // weakly-up -> up
            } else {
              n.status === Joining // joining -> up
            }

          isJoining || n.status === Up || n.status === Leaving
        }

    val totalConsideredNodes = reachableConsideredNodes.size + unreachableConsideredNodes.size

    val majority = Math.max(totalConsideredNodes / 2 + 1, 1)

    if (reachableConsideredNodes.size >= majority) {
      Decision.downUnreachable(worldView).pure[F]
    } else if (unreachableConsideredNodes.size >= majority)
      Decision.downReachable(worldView).pure[F]
    else if (totalConsideredNodes > 0 && reachableConsideredNodes.size === unreachableConsideredNodes.size) {
      // check if the node with the lowest unique address is in this partition
      (reachableConsideredNodes ++ unreachableConsideredNodes).toList.sorted.headOption
        .fold(KeepMajority.NoMajority.raiseError[F, Decision]) {
          case _: ReachableNode   => Decision.downUnreachable(worldView).pure[F]
          case _: UnreachableNode => Decision.downReachable(worldView).pure[F]
        }
    } else {
      // There are no nodes with the configured role in the cluster so
      // there is no partition with a majority. In this case we make
      // the safe decision to down the current partition.
      Decision.downReachable(worldView).pure[F]
    }
  }

  override def toString: String = s"KeepMajority($config)"
}

private[lithium] object KeepMajority {

  /**
   * [[KeepMajority]] configuration.
   *
   * @param role the role of the nodes to take in account.
   */
  final case class Config(role: String)

  object Config extends StrategyReader[Config] {
    override val name: String = "keep-majority"
  }

  case object NoMajority extends Throwable

}
