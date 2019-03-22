package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr._
import akka.cluster.sbr.strategies.keepmajority.KeepMajorityView.NoMajority
import cats.implicits._

/**
 * Represents the "Keep Majority" split-brain resolution strategy.
 *
 * @param role the role of nodes to take in account.
 */
final case class KeepMajority(role: String)

object KeepMajority {

  /**
   * Strategy that will down a partition if it is not a majority. In case of an even number of nodes
   * it will choose the partition with the lowest address.
   *
   * A `role` can be provided to only take in account the nodes with that role in the decisions.
   * This can be useful if there are nodes that are more important than others.
   */
  implicit val keepMajorityStrategy: Strategy[KeepMajority] = new Strategy[KeepMajority] {
    override def handle(strategy: KeepMajority, worldView: WorldView): Either[Throwable, StrategyDecision] =
      KeepMajorityView(worldView, strategy.role)
        .map {
          // Survive if this partition is a majority or contains the lowest address...
          case ReachableMajority | ReachableLowestAddress => DownUnreachable(worldView)

          // ...else down itself.
          case UnreachableMajority | UnreachableLowestAddress => DownReachable(worldView)
        }
        .recoverWith {
          case NoMajority => DownReachable(worldView).asRight
        }
  }

  implicit val keepMajorityStrategyReader: StrategyReader[KeepMajority] = StrategyReader.fromName("keep-majority")

//  implicit val keepMajorityReader: ConfigReader[KeepMajority] = deriveReader[KeepMajority]
}
