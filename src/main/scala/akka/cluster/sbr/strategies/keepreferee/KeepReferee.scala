package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr._
import cats.data.NonEmptySet
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

final case class KeepReferee()

object KeepReferee {

  /**
   * The config for the [[KeepReferee]] strategy.
   *
   * @param address the address of the referee.
   * @param downAllIfLessThanNodes the minimum number of nodes in a partition else it will be downed.
   */
  final case class Config(address: String, downAllIfLessThanNodes: Int Refined Positive)

  def keepReferee(worldView: WorldView, config: Config): StrategyDecision =
    KeepRefereeView(worldView, config) match {
      case RefereeReachable =>
        NonEmptySet.fromSet(worldView.unreachableNodes).fold[StrategyDecision](Idle)(DownUnreachable)

      case TooFewReachableNodes =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable)

      case RefereeUnreachable =>
        NonEmptySet.fromSet(worldView.reachableNodes).fold[StrategyDecision](Idle)(DownReachable)
    }

  implicit val keepRefereeStrategy: Strategy.Aux[KeepReferee, KeepReferee.Config] = new Strategy[KeepReferee] {
    override type Config = KeepReferee.Config
    override val name: String = "keep-referee"
    override def handle(worldView: WorldView, config: KeepReferee.Config): Either[Throwable, StrategyDecision] =
      keepReferee(worldView, config).asRight
  }
}
