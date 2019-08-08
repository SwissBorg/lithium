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
import com.swissborg.lithium.implicits._
import com.swissborg.lithium.internals._
import com.swissborg.lithium.reporter.SplitBrainReporter._

import scala.collection.immutable.SortedSet

/**
  * Actor reporting the reachability status of cluster members based on `akka.cluster.Reachability`.
  *
  * @param splitBrainReporter the actor to which the reachability events have to be sent.
  */
private[lithium] class ReachabilityReporter(private val splitBrainReporter: ActorRef)
    extends Actor
    with ActorLogging
    with Stash {

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
    case LithiumReachabilityChanged(r) =>
      context.become(active(updateReachability(r).runS(state).unsafeRunSync()))

    case LithiumSeenChanged(_, seenBy) =>
      context.become(active(updateSeenBy(seenBy).runS(state).unsafeRunSync()))

    case MemberJoined(m) =>
      context.become(active(add(m).runS(state).unsafeRunSync()))

    case MemberRemoved(m, _) =>
      context.become(active(remove(m.uniqueAddress).runS(state).unsafeRunSync()))
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

  private def add(member: Member): F[Unit] = StateT.modify(_.withMember(member))

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

  private def members: F[SortedSet[Member]] = StateT.liftF(SyncIO(cluster.state.members))

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent])
    discard(ClusterInternals(context.system).subscribeToReachabilityChanged(self))
    discard(ClusterInternals(context.system).subscribeToSeenChanged(self))
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    ClusterInternals(context.system).unsubscribe(self)
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
