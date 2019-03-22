package akka.cluster.sbr.strategies.keepreferee

import akka.cluster.sbr._
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.pureconfig._ // DO NOT REMOVE
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

final case class KeepReferee()

object KeepReferee {

  /**
   * The config for the [[KeepReferee]] strategy.
   *
   * @param address the address of the referee.
   * @param downAllIfLessThanNodes the minimum number of nodes in a partition else it will be downed.
   */
  final case class Config(address: String, downAllIfLessThanNodes: Int Refined Positive)

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  }

  def keepReferee(worldView: WorldView, config: Config): StrategyDecision = {
    val a = KeepRefereeView(worldView, config)
//    println(a)
    a match {
      case RefereeReachable                          => DownUnreachable(worldView)
      case TooFewReachableNodes | RefereeUnreachable => DownReachable(worldView)
    }
  }

  implicit val keepRefereeStrategy: Strategy.Aux[KeepReferee, KeepReferee.Config] = new Strategy[KeepReferee] {
    override type Config = KeepReferee.Config
    override val name: String = "keep-referee"
    override def handle(worldView: WorldView, config: KeepReferee.Config): Either[Throwable, StrategyDecision] =
      keepReferee(worldView, config).asRight
  }
}
