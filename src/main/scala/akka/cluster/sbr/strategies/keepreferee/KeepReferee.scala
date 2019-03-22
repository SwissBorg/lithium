package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr._
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

final case class KeepReferee(address: String, downAllIfLessThanNodes: Int Refined Positive)

object KeepReferee {
  def keepReferee(strategy: KeepReferee, worldView: WorldView): StrategyDecision = {
    val a = KeepRefereeView(worldView, strategy.address, strategy.downAllIfLessThanNodes)
//    println(a)
    a match {
      case RefereeReachable                          => DownUnreachable(worldView)
      case TooFewReachableNodes | RefereeUnreachable => DownReachable(worldView)
    }
  }

  implicit val keepRefereeStrategy: Strategy[KeepReferee] = new Strategy[KeepReferee] {
    override def handle(strategy: KeepReferee, worldView: WorldView): Either[Throwable, StrategyDecision] =
      keepReferee(strategy, worldView).asRight
  }

  implicit val keepRefereeStrategyReader: StrategyReader[KeepReferee] = StrategyReader.fromName("keep-referee")

//  implicit val keepRefereeReader: ConfigReader[KeepReferee] = deriveReader[KeepReferee]
}
