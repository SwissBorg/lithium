package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr.WorldView
import akka.cluster.sbr.strategies.keepreferee.KeepReferee.Config
import eu.timepit.refined.auto._

sealed abstract private[keepreferee] class KeepRefereeView extends Product with Serializable

private[keepreferee] object KeepRefereeView {
  def apply(worldView: WorldView, config: Config): KeepRefereeView =
    worldView.reachableConsideredNodes
      .find(_.node.address.toString == config.address)
      .fold[KeepRefereeView](RefereeUnreachable) { _ =>
        if (worldView.reachableConsideredNodes.size < config.downAllIfLessThanNodes) TooFewReachableNodes
        else RefereeReachable
      }
}

final private[keepreferee] case object RefereeReachable     extends KeepRefereeView
final private[keepreferee] case object TooFewReachableNodes extends KeepRefereeView
final private[keepreferee] case object RefereeUnreachable   extends KeepRefereeView
