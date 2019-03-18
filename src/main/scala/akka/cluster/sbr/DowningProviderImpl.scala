package akka.cluster.sbr

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.cluster.{Cluster, DowningProvider}
import akka.cluster.sbr.strategies.keepmajority.KeepMajority
import akka.cluster.sbr.strategies.keepoldest.KeepOldest
import akka.cluster.sbr.strategies.keepreferee.KeepReferee
import akka.cluster.sbr.strategies.staticquorum.StaticQuorum
import cats.implicits._
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration

class DowningProviderImpl(system: ActorSystem) extends DowningProvider {
  val conf = ConfigFactory.load()

  override def downRemovalMargin: FiniteDuration = FiniteDuration(5, "seconds")
//    conf.getString("akka.cluster.split-brain-resolver.stable-after")

  override def downingActorProps: Option[Props] = {
    val strategyName: String = conf.getString("akka.cluster.split-brain-resolver.active-strategy")

    val keepMajority = Strategy.name[KeepMajority]
    val keepOldest   = Strategy.name[KeepOldest]
    val keepReferee  = Strategy.name[KeepReferee]
    val staticQuorum = Strategy.name[StaticQuorum]

    strategyName match {
      case `keepMajority` =>
        Strategy[KeepMajority]
          .fromConfig[KeepMajority.Config]
          .map(
            Downer
              .props(Cluster(system), _, FiniteDuration(5, "seconds"))
          )
          .fold(throw new IllegalArgumentException("bla"), _.some)

      case `keepOldest` =>
        Strategy[KeepOldest]
          .fromConfig[KeepOldest.Config]
          .map(Downer.props(Cluster(system), _, FiniteDuration(5, "seconds")))
          .fold(throw new IllegalArgumentException("bla"), _.some)

      case `keepReferee` =>
        Strategy[KeepReferee]
          .fromConfig[KeepReferee.Config]
          .map(Downer.props(Cluster(system), _, FiniteDuration(5, "seconds")))
          .fold(throw new IllegalArgumentException("bla"), _.some)

      case `staticQuorum` =>
        val a = Strategy[StaticQuorum]
          .fromConfig[StaticQuorum.Config]

        a.map(Downer.props(Cluster(system), _, FiniteDuration(5, "seconds")))
          .fold(err => throw new IllegalArgumentException(err.toString), _.some)

      case _ => None
    }
  }
}
