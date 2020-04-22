package com.swissborg.lithium

package reachability

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster._
import akka.cluster.swissborg._
import cats.data._
import cats.effect._
import cats.implicits._
import cats.{~>, Eval}
import com.swissborg.lithium.internals._
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

  override def receive: Receive = active(ReachabilityReporterState(cluster.selfDataCenter))

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def active(state: ReachabilityReporterState): Receive = {
    case LithiumReachabilityChanged(r) =>
      context.become(active(updateReachability(r).runS(state).unsafeRunSync()))

    case LithiumSeenChanged(_, seenBy) =>
      context.become(active(updateSeenBy(seenBy).runS(state).unsafeRunSync()))

    case RetrieveSnapshot =>
      context.become(active(updateFromSnapshot(cluster.state).runS(state).unsafeRunSync()))
  }

  private def updateReachability(reachability: LithiumReachability): F[Unit] =
    for {
      events <- ReachabilityReporterState.withReachability(reachability).mapK(evalToSyncIO)
      _      <- events.traverse_(sendToReporter)
    } yield ()

  private def updateSeenBy(seenBy: Set[Address]): F[Unit] =
    for {
      events <- ReachabilityReporterState.withSeenBy(seenBy).mapK(evalToSyncIO)
      _      <- events.traverse_(sendToReporter)
    } yield ()

  private def updateFromSnapshot(snapshot: CurrentClusterState): F[Unit] =
    StateT.modify(_.withMembers(snapshot.members))

  private def sendToReporter(reachabilityEvent: NodeReachabilityEvent): F[Unit] =
    StateT.liftF(SyncIO(splitBrainReporter ! reachabilityEvent))

  override def preStart(): Unit = {
    timers.startTimerAtFixedRate(RetrieveSnapshot, RetrieveSnapshot, 100.millis)
    ClusterInternals(context.system).subscribeToReachabilityChanged(self)
    ClusterInternals(context.system).subscribeToSeenChanged(self)
  }

  override def postStop(): Unit = {
    timers.cancelAll()
    ClusterInternals(context.system).unsubscribe(self)
  }
}

private[lithium] object ReachabilityReporter {
  private type F[A] = StateT[SyncIO, ReachabilityReporterState, A]

  case object RetrieveSnapshot

  def props(sendTo: ActorRef): Props = Props(new ReachabilityReporter(sendTo))

  private val evalToSyncIO: Eval ~> SyncIO = new ~>[Eval, SyncIO] {
    override def apply[A](fa: Eval[A]): SyncIO[A] = SyncIO.eval(fa)
  }
}
