package akka.cluster.sbr

import akka.actor.{ActorContext, ActorRef, Address, Cancellable}
import akka.cluster.ClusterEvent.ReachabilityEvent
import akka.cluster.Member
import akka.cluster.sbr.Downer.ReachabilityNotification
import akka.cluster.sbr.ReachabilityNotifier.NotificationJob
import cats.effect.SyncIO
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Provides a way to notify other nodes of reachability events related to them.
 */
final case class ReachabilityNotifier(private val jobs: Map[Address, NotificationJob],
                                      private val send: (Member, Any) => SyncIO[Unit]) {

  /**
   * Notify the member association with the event.
   */
  def notify(
    reachabilityEvent: ReachabilityEvent
  )(implicit context: ActorContext, ec: ExecutionContext, self: ActorRef): SyncIO[ReachabilityNotifier] = {
    val address = reachabilityEvent.member.address

    for {
      id               <- nextId(address)
      sendNotification <- send(reachabilityEvent.member, ReachabilityNotification(reachabilityEvent, self, id))
      sendNotification <- SyncIO(context.system.scheduler.schedule(0.seconds, 1.second)(sendNotification))
    } yield copy(jobs = jobs + (address -> NotificationJob(id, sendNotification)))
  }

  /**
   * Acknowledge the notification to `member` with the given id.
   */
  def ack(member: Member, id: Long): SyncIO[ReachabilityNotifier] = {
    val address = member.address

    jobs
      .get(address)
      .traverse {
        case NotificationJob(id0, sendNotification) =>
          if (id0 == id) {
            SyncIO(sendNotification.cancel()).void
          } else {
            SyncIO.unit // stale or inexistent: ignore
          }
      }
      .as(copy(jobs = jobs - address))
  }

  /**
   * Cancels the snitching to `member`.
   */
  def cancel(member: Member): SyncIO[ReachabilityNotifier] = {
    val address = member.address
    jobs.get(address).traverse(job => SyncIO(job.sendNotification.cancel())).as(copy(jobs = jobs - address))
  }

  def cancelAll(): SyncIO[ReachabilityNotifier] =
    jobs.values.toList.traverse(job => SyncIO(job.sendNotification.cancel())).as(copy(jobs = Map.empty))

  /**
   * Generate an id for the `address`.
   */
  private def nextId(address: Address): SyncIO[Long] = jobs.get(address).fold(SyncIO.pure(0L)) {
    case NotificationJob(id, sendNotification) => SyncIO(sendNotification.cancel()).as(id + 1)
  }
}

object ReachabilityNotifier {
  def apply(send: (Member, Any) => SyncIO[Unit]): ReachabilityNotifier =
    ReachabilityNotifier(Map.empty, send)

  final case class NotificationJob(id: Long, sendNotification: Cancellable)
}
