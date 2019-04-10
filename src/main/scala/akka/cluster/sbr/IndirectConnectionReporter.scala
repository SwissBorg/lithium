package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Cancellable, Props}
import akka.cluster.ClusterEvent.ReachabilityEvent
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, SendToAll}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

// If a node receive a unreachability event in his name it means that it is
// indirectly connected. It is unreachable via a link but reachable via another as
// it receive the event.
// As cluster events are only gossiped to reachable nodes,
// a node that has been detected as unreachable will never receive an unreachability
// event in his name.

class IndirectConnectionReporter(parent: ActorRef) extends Actor with ActorLogging {

  import IndirectConnectionReporter._

  var statuses: Map[Address, JobStatus] = Map.empty

  private val mediator = DistributedPubSub(context.system).mediator
  mediator ! Put(self)

  override def receive: Receive = {
    case Report(e, to) =>
      log.debug("Reporting {} to {}", e, to)
      val (status, maybeId) = nextId(to)

      maybeId match {
        case Some(id) => statuses = statuses + (to -> status.copy(jobs = status.jobs + (id -> report(e, id, to))))
        case None     => statuses = statuses + (to -> status)
      }

    case Ack(id, from) =>
      // Broadcast ack to the notifiers on different nodes.
      mediator ! SendToAll(self.path.toStringWithoutAddress, PublishAck(id, from), allButSelf = true)

    case PublishAck(id, from) =>
      log.debug("Received acknowledgment for {} from {}.", id, from)
      statuses.get(from) match {
        case Some(status) =>
          status.jobs.get(id).foreach(_.cancel())
          statuses = statuses + (from -> status.copy(jobs = status.jobs - id))
        case None =>
          statuses = statuses + (from -> JobStatus.withStashedAck(id))
      }

  }

  private def nextId(address: Address): (JobStatus, Option[Long]) = {
    val status = statuses.getOrElse(address, JobStatus.empty)

    val potentialNextId =
      if (status.jobs.keys.nonEmpty) {
        status.jobs.keys.max + 1
      } else {
        0L
      }

    if (status.stashedAcks.contains(potentialNextId)) {
      // The event for which the id is being generated will be ignored
      // and "automatically" be acked.
      (status.copy(stashedAcks = status.stashedAcks - potentialNextId), None)
    } else {
      statuses = statuses + (address -> status)
      (status, Some(potentialNextId))
    }
  }

  private def report(e: ReachabilityEvent, id: Long, to: Address): Cancellable = {
    val path  = s"$to${parent.path.toStringWithoutAddress}"
    val actor = context.actorSelection(path)
    log.debug(s"Send: $actor ! $e")
    context.system.scheduler.schedule(0.seconds, 1.second)(actor ! Notification(e, id))
  }

  implicit private val ec: ExecutionContext = context.system.dispatcher

  override def postStop(): Unit =
    statuses.values.foreach(_.jobs.values.foreach(_.cancel()))
}

object IndirectConnectionReporter {
  def props(parent: ActorRef): Props = Props(new IndirectConnectionReporter(parent))

  final case class Report(e: ReachabilityEvent, to: Address)
  final case class Ack(id: Long, address: Address)

  /**
   * For internal use.
   */
  final case class PublishAck(id: Long, address: Address)

  final case class Notification(e: ReachabilityEvent, id: Long)

  final case class JobStatus(jobs: Map[Long, Cancellable], stashedAcks: Set[Long])
  object JobStatus {
    val empty: JobStatus                    = JobStatus(Map.empty, Set.empty)
    def withStashedAck(id: Long): JobStatus = JobStatus(Map.empty, Set(id))
  }
}
