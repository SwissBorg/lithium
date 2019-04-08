package akka.cluster.sbr

import akka.actor.{ActorContext, ActorRef, Address, Cancellable}
import akka.cluster.ClusterEvent.ReachabilityEvent
import akka.cluster.Member
import akka.cluster.sbr.Downer.ReachabilityNotification
import akka.cluster.sbr.Notifier.NotificationJob
import cats.effect.SyncIO
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Provides machinery to send out notifications.
 *
 * @param jobs the notification jobs in progress.
 * @param send how to send a message of type `E` and its id.
 * @tparam E the type of notification messages.
 */
final case class Notifier[E](
  private val jobs: Map[Address, NotificationJob],
  private val send: (E, Long) => SyncIO[Unit]
)(implicit context: ActorContext, ec: ExecutionContext) {

  /**
   * Send out a notification with the message `e` and expect an acknowledgment from `expectAckFrom`.
   */
  def notify(
    e: E,
    expectAckFrom: Address
  ): SyncIO[Notifier[E]] =
    for {
      id               <- nextId(expectAckFrom)
      sendNotification <- SyncIO(context.system.scheduler.schedule(0.seconds, 1.second)(send(e, id).unsafeRunSync())) // todo correctly set the frequency
    } yield copy(jobs = jobs + (expectAckFrom -> NotificationJob(id, sendNotification)))

  /**
   * Acknowledge the notification.
   */
  def ack(acker: Address, id: Long): SyncIO[Notifier[E]] =
    jobs
      .get(acker)
      .traverse {
        case NotificationJob(id0, sendNotification) =>
          if (id0 <= id) {
            // Cancel the sending of all previous notifications.
            // When a node drags behind the others nodes this will
            // make sure that it will not send out stale notifications.
            SyncIO(sendNotification.cancel()).void
          } else {
            SyncIO.unit // stale ack
          }
      }
      .as(copy(jobs = jobs - acker))

  /**
   * Cancels the snitching to `member`.
   */
  def cancel(member: Member): SyncIO[Notifier[E]] = {
    val address = member.address
    jobs.get(address).traverse(job => SyncIO(job.sendNotification.cancel())).as(copy(jobs = jobs - address))
  }

  def cancelAll(): SyncIO[Notifier[E]] =
    jobs.values.toList.traverse(job => SyncIO(job.sendNotification.cancel())).as(copy(jobs = Map.empty))

  /**
   * Generate an id for the `address`.
   */
  private def nextId(address: Address): SyncIO[Long] = jobs.get(address).fold(SyncIO.pure(0L)) {
    case NotificationJob(id, sendNotification) => SyncIO(sendNotification.cancel()).as(id + 1)
  }
}

object Notifier {
  def apply[E](send: (E, Long) => SyncIO[Unit])(implicit context: ActorContext, ec: ExecutionContext): Notifier[E] =
    Notifier(Map.empty, send)

  final case class NotificationJob(id: Long, sendNotification: Cancellable)
}
