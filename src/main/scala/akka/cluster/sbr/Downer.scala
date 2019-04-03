package akka.cluster.sbr

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Cancellable, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.sbr.strategies.Or
import akka.cluster.sbr.strategies.indirected.Indirected
import cats.effect.SyncIO
import akka.cluster.sbr.strategies.downall.DownAll
import akka.cluster.sbr.implicits._
import akka.cluster.sbr.strategy.Strategy
import akka.cluster.sbr.strategy.ops._
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Downer[A: Strategy](cluster: Cluster,
                          strategy: A,
                          stableAfter: FiniteDuration,
                          downAllWhenUnstable: FiniteDuration)
    extends Actor
    with ActorLogging {

  import Downer._

  // TODO is this one ok?
  implicit private val ec: ExecutionContext = context.system.dispatcher

  // If a node receive a unreachability event in his name it means that it is
  // indirectly connected. It is unreachable via a link but reachable via another as
  // it receive the event.
  // As cluster events are only gossiped to reachable nodes,
  // a node that has been detected as unreachable will never receive an unreachability
  // event in his name.
  private val mediator = DistributedPubSub(cluster.system).mediator
  mediator ! Subscribe(cluster.selfAddress.toString, context.self)

  override def receive: Receive = waitingForSnapshot.andThen(_.unsafeRunSync())

  /**
   * Waits for the state snapshot we should get after having
   * subscribed to the cluster's state with the initial
   * state as snapshot.
   */
  private def waitingForSnapshot: IOReceive = {
    case state: CurrentClusterState =>
      val worldView = WorldView(cluster.selfMember, state)

      if (worldView.unreachableNodes.nonEmpty) {
        for {
          stability   <- scheduleStability
          instability <- scheduleInstability
          notifier <- worldView.unreachableNodes
            .foldLeft(SyncIO.pure(ReachabilityNotifier(publish))) {
              case (notifier, node) =>
                notifier.flatMap(notifyIfReachable(_, UnreachableMember(node.member))) // todo correct?
            }
          _ <- become(hasUnreachableNodes(worldView, stability, instability, notifier))
        } yield ()
      } else {
        become(noUnreachableNodes(worldView, ReachabilityNotifier(publish)))
      }

    case _ => SyncIO.unit // ignore // TODO needed?
  }

  private def publish[A](topic: String, a: A): SyncIO[Unit] = SyncIO(mediator ! Publish(topic, a))

  /**
   * Actor's state when the cluster has no unstable nodes.
   *
   * At this point the unstability message has not been scheduled yet.
   *
   */
  private def noUnreachableNodes(worldView: WorldView, notifier: ReachabilityNotifier): IOReceive = {
    case e: MemberEvent =>
      println(s"EVENT0: $e")
      for {
        worldView <- SyncIO.pure(worldView.memberEvent(e))
        _         <- become(noUnreachableNodes(worldView, notifier))
      } yield ()

    case e: UnreachableMember =>
      println(s"EVENT0: $e")

      for {
        worldView   <- SyncIO.pure(worldView.reachabilityEvent(e))
        stability   <- scheduleStability
        instability <- scheduleInstability
        notifier    <- notifyIfReachable(notifier, e)
        _           <- become(hasUnreachableNodes(worldView, stability, instability, notifier))
      } yield ()

    case e: ReachableMember =>
      println(s"EVENT0: $e")

      for {
        worldView <- SyncIO.pure(worldView.reachabilityEvent(e))
        notifier  <- notifyIfReachable(notifier, e)
        _         <- become(noUnreachableNodes(worldView, notifier))
      } yield ()

    case r @ ReachabilityNotification(event, ackTo, _) =>
      println(s"REQUEST: $r")
      if (event.member === cluster.selfMember) {
        for {
          _         <- SyncIO(ackTo ! r.ack)
          worldView <- SyncIO.pure(worldView.reachabilityEvent(event))

          _ <- if (worldView.unreachableNodes.isEmpty) {
            become(noUnreachableNodes(worldView, notifier))
          } else {
            for {
              stability   <- scheduleStability
              instability <- scheduleInstability
              _           <- become(hasUnreachableNodes(worldView, stability, instability, notifier))
            } yield ()
          }
        } yield ()
      } else {
        SyncIO.unit
      }

    case s @ ReachabilityNotificationAck(event, id) =>
      println(s"RESPONSE: $s")
      for {
        notifier <- notifier.ack(event.member, id)
        _        <- become(noUnreachableNodes(worldView, notifier))
      } yield ()
  }

  private def cancel(cancellable: Cancellable): SyncIO[Unit] = SyncIO(cancellable.cancel()).void

  private def notifyIfReachable(notifier: ReachabilityNotifier, e: ReachabilityEvent): SyncIO[ReachabilityNotifier] =
    if (cluster.failureDetector.isAvailable(e.member.address)) notifier.notify(e)
    else SyncIO.pure(notifier)

  private def resetWhen(p: Boolean)(cancellable: Cancellable, start: SyncIO[Cancellable]): SyncIO[Cancellable] =
    if (p) cancel(cancellable) >> start
    else SyncIO.pure(cancellable)

  /**
   * Actor's state when the cluster contains at least one unstable node.
   *
   */
  private def hasUnreachableNodes(worldView: WorldView,
                                  stability: Cancellable,
                                  instability: Cancellable,
                                  notifier: ReachabilityNotifier): IOReceive = {
    case ClusterIsStable =>
      println("hasUnreachableNodes")

      for {
        instability <- cancel(instability) >> scheduleInstability
        stability   <- cancel(stability) >> scheduleStability
        _           <- runStrategy(worldView)
        _           <- become(hasUnreachableNodes(worldView, stability, instability, notifier)) // todo maybe not retrigger timeouts
      } yield ()

    case ClusterIsUnstable =>
      for {
        instability <- cancel(instability) >> scheduleInstability
        stability   <- cancel(stability) >> scheduleStability
        _           <- downAllNodes(worldView)
        _           <- become(hasUnreachableNodes(worldView, stability, instability, notifier)) // todo maybe not retrigger timeouts
      } yield ()

    case e: MemberEvent =>
      println(s"EVENT1: $e")

      for {
        worldView0 <- SyncIO.pure(worldView.memberEvent(e))
        stability  <- resetWhen(!worldView0.isStableChange(worldView))(stability, scheduleStability)
        _          <- become(hasUnreachableNodes(worldView0, stability, instability, notifier))
      } yield ()

    case e: ReachabilityEvent =>
      println(s"EVENT1: $e")

      for {
        worldView0 <- SyncIO.pure(worldView.reachabilityEvent(e))
        notifier   <- notifyIfReachable(notifier, e)
        _ <- if (worldView0.unreachableNodes.isEmpty) {
          for {
            _ <- cancel(instability)
            _ <- cancel(stability)
            _ <- become(noUnreachableNodes(worldView0, notifier))
          } yield ()
        } else {
          become(hasUnreachableNodes(worldView0, stability, instability, notifier))
        }
      } yield ()

    case r @ ReachabilityNotification(event, ackTo, _) =>
      println(s"REQUEST: $r")
      if (event.member === cluster.selfMember) {
        for {
          _          <- SyncIO(ackTo ! r.ack)
          worldView0 <- SyncIO.pure(worldView.reachabilityEvent(event))

          _ <- if (worldView0.unreachableNodes.isEmpty) {
            for {
              _ <- cancel(stability)
              _ <- cancel(instability)
              _ <- become(noUnreachableNodes(worldView0, notifier))
            } yield ()
          } else {
            for {
              stability <- resetWhen(!worldView0.isStableChange(worldView))(stability, scheduleStability)
              _         <- become(hasUnreachableNodes(worldView0, stability, instability, notifier))
            } yield ()
          }
        } yield ()
      } else {
        SyncIO.unit
      }

    case s @ ReachabilityNotificationAck(event, ackN) =>
      println(s"RESPONSE: $s")
      for {
        notifier <- notifier.ack(event.member, ackN)
        _        <- become(hasUnreachableNodes(worldView, stability, instability, notifier))
      } yield ()
  }

  /**
   * Runs [[strategy]] with the strategy to remove indirectly connected nodes.
   */
  private def runStrategy(worldView: WorldView): SyncIO[Unit] = {
    println(s"WV: $worldView")
    for {
      decision <- SyncIO.fromEither(Or(strategy, Indirected).takeDecision(worldView))
      _        <- executeDecision(decision)
    } yield ()
  }

  /**
   * Downs all the nodes in the cluster.
   */
  private def downAllNodes(worldView: WorldView): SyncIO[Unit] =
    for {
      decision <- SyncIO.fromEither(DownAll.takeDecision(worldView))
      _        <- executeDecision(decision)
    } yield ()

  /**
   * Executes the decision.
   *
   * If the current node is the leader all the nodes referred in the decision
   * will be downed. Otherwise, if it is not the leader or none exists, and refers to itself.
   * It will down the current node. Else, no node will be downed.
   *
   * In short, the leader can down anyone. Other nodes are only allowed to down themselves.
   */
  private def executeDecision(decision: StrategyDecision): SyncIO[Unit] = SyncIO {
    println(s"DECISION: $decision")
    if (cluster.state.leader.contains(cluster.selfAddress)) {
      val nodesToDown = decision.nodesToDown
      println(s"Downing nodes: $nodesToDown")
      nodesToDown.foreach(node => cluster.down(node.member.address))
    } else {
      if (decision.nodesToDown.map(_.member).contains(cluster.selfMember)) {
        println(s"Downing self: $cluster.selfMember")
        cluster.down(cluster.selfAddress)
      } else {
        println("Non-leader cannot down other nodes.")
      }
    }
  }

  private def scheduleStability: SyncIO[Cancellable] =
    SyncIO(context.system.scheduler.scheduleOnce(stableAfter, self, ClusterIsStable))

  /**
   * Schedules an instability trigger. In parallel also start a related timeout that will be
   * used to cancel the instability trigger.
   *
   * @return a handle that will cancel both the instability trigger and the related timeout.
   */
  private def scheduleInstability: SyncIO[Cancellable] =
    SyncIO {
      val c1 = context.system.scheduler.scheduleOnce(stableAfter * 2, self, ClusterIsUnstableTimeout)
      val c2 = context.system.scheduler.scheduleOnce(stableAfter + downAllWhenUnstable, self, ClusterIsUnstable)

      new Cancellable {
        private val b: AtomicBoolean = new AtomicBoolean(c1.isCancelled && c2.isCancelled)

        override def cancel(): Boolean = c1.cancel() && c2.cancel()

        override def isCancelled: Boolean = b.getAndSet(c1.isCancelled && c2.isCancelled)
      }
    }

  override def preStart(): Unit =
    cluster.subscribe(self,
                      InitialStateAsSnapshot,
                      classOf[akka.cluster.ClusterEvent.MemberEvent],
                      classOf[akka.cluster.ClusterEvent.ReachabilityEvent])

  override def postStop(): Unit = cluster.unsubscribe(self)
}

object Downer {
  type IOReceive = PartialFunction[Any, SyncIO[Unit]]

  def props[A: Strategy](cluster: Cluster,
                         strategy: A,
                         stableAfter: FiniteDuration,
                         downAllWhenUnstable: FiniteDuration): Props =
    Props(new Downer(cluster, strategy, stableAfter, downAllWhenUnstable))

  def become(receive: IOReceive)(implicit context: ActorContext): SyncIO[Unit] =
    SyncIO(context.become(receive.andThen(_.unsafeRunSync())))

  final case object ClusterIsStable
  final case object ClusterIsUnstable
  final case object ClusterIsUnstableTimeout

  final case class ReachabilityNotification(reachabilityEvent: ReachabilityEvent, ackTo: ActorRef, id: Long) {
    def ack: ReachabilityNotificationAck = ReachabilityNotificationAck(reachabilityEvent, id)
  }

  final case class ReachabilityNotificationAck(reachabilityEvent: ReachabilityEvent, id: Long)
}
