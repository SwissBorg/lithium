package com.swissborg.lithium

package reachability

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster._
import akka.cluster.swissborg._
import cats.{~>, Eval}
import cats.data._
import cats.effect._
import cats.implicits._
import com.swissborg.lithium.converter._
import com.swissborg.lithium.implicits._
import com.swissborg.lithium.reporter.SplitBrainReporter._

import scala.concurrent.duration._

/**
  * Actor reporting the reachability status of cluster members based on `akka.cluster.Reachability`.
  *
  * @param splitBrainReporter the actor to which the reachability events have to be sent.
  */
private[lithium] class ReachabilityReporter(private val splitBrainReporter: ActorRef)
    extends Actor
    with ActorLogging
    with Stash
    with Timers {
  import ReachabilityReporter._

  private val cluster = Cluster(context.system)

  private val selfUniqueAddress = cluster.selfUniqueAddress

  override def receive: Receive = initializing

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def initializing: Receive = {
    case snapshot: CurrentClusterState =>
      unstashAll()
      context.become(
        active(ReachabilityReporterState.fromSnapshot(snapshot, cluster.selfDataCenter))
      )

    case _ => stash()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def active(state: ReachabilityReporterState): Receive = {
    case RetrieveMembership =>
      context.become(active(updateReachabilitiesFromSnapshot(cluster.state).runS(state).unsafeRunSync()))

    case LithiumReachabilityChanged(r) =>
      context.become(active(updateReachability(r).runS(state).unsafeRunSync()))

    case MemberJoined(m) =>
      context.become(active(add(m).runS(state).unsafeRunSync()))

    case MemberRemoved(m, _) =>
      context.become(active(remove(m.uniqueAddress).runS(state).unsafeRunSync()))
  }

  private def updateReachabilitiesFromSnapshot(snapshot: CurrentClusterState): F[Unit] =
    ReachabilityReporterState
      .updateReachabilities(snapshot)
      .mapK(evalToSyncIO)
      .flatMap(_.traverse_(sendToReporter))

  private def updateReachability(reachability: LithiumReachability): F[Unit] =
    StateT.modify(_.withReachability(reachability))

  private def add(member: Member): F[Unit] = StateT.modify(_.addOtherDcMember(member))

  private def sendToReporter(reachabilityEvent: NodeReachabilityEvent): F[Unit] =
    StateT.liftF(SyncIO(splitBrainReporter ! reachabilityEvent))

  /**
    * Register the node as removed.
    *
    * If the removed node is the current one the actor will stop itself.
    */
  private def remove(node: UniqueAddress): F[Unit] =
    if (node === selfUniqueAddress) {
      // This node is being stopped. Kill the actor
      // to stop any further updates.
      StateT.liftF(SyncIO(context.stop(self)))
    } else {
      StateT.modify(_.remove(node))
    }

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
    timers.startPeriodicTimer(RetrieveMembership, RetrieveMembership, 100.millis)
    discard(LithiumReachabilityPub(context.system).subscribeToReachabilityChanged(self))
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    LithiumReachabilityPub(context.system).unsubscribe(self)
    timers.cancelAll()
  }
}

private[lithium] object ReachabilityReporter {
  private type F[A] = StateT[SyncIO, ReachabilityReporterState, A]

  case object RetrieveMembership

  def props(sendTo: ActorRef): Props = Props(new ReachabilityReporter(sendTo))

  private val evalToSyncIO: Eval ~> SyncIO = new ~>[Eval, SyncIO] {
    override def apply[A](fa: Eval[A]): SyncIO[A] = SyncIO.eval(fa)
  }
}
