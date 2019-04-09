package akka.cluster.sbr

import akka.actor.{Address, Cancellable}
import akka.cluster.sbr.Notifier.JobStatus
import cats.data.OptionT
import cats.effect
import cats.effect.SyncIO

final case class Notifier[E] private (statuses: Map[Address, JobStatus],
                                      send: (E, Address, Long) => SyncIO[Cancellable]) {

  /**
   * Send `e` using [[send]].
   *
   * It assumes that all users of this class send the same events, in the same order, to `to`.
   * As a result, if the messages has already been acked by `to` it will not be sent a second time.
   *
   * @param e the event to send.
   * @param to the address that should ack the event.
   */
  def send(e: E, to: Address): SyncIO[Notifier[E]] = {
    val notifier0 = for {
      (status, maybeId) <- OptionT.liftF(SyncIO.pure(nextId(to)))
      id                <- OptionT.fromOption[SyncIO](maybeId)
      sendNotification  <- OptionT.liftF(send(e, to, id))
    } yield copy(statuses = statuses + (to -> status.copy(jobs = status.jobs + (id -> sendNotification))))

    notifier0.getOrElse(this)
  }

  /**
   * Acknowledge the event.
   *
   * @param id the id of the event to be acked.
   * @param from the address of the acker.
   */
  def ack(id: Long, from: Address): SyncIO[Notifier[E]] = {
    def cancelAndUpdate(status: JobStatus): OptionT[SyncIO, JobStatus] =
      for {
        c <- OptionT.fromOption[effect.SyncIO](status.jobs.get(id))
        _ <- OptionT.liftF(Downer.cancel(c))
      } yield status.copy(jobs = status.jobs - id)

    val updateJobStatus = for {
      status <- OptionT.fromOption[SyncIO](statuses.get(from))
      status <- OptionT.liftF(cancelAndUpdate(status).getOrElse(status.copy(stashedAcks = status.stashedAcks + id)))
    } yield status

    // Create an empty job status with ack stashed as the related job does not exist.
    updateJobStatus.fold(copy(statuses = statuses + (from -> JobStatus(Map.empty, Set(id))))) { status =>
      copy(statuses = statuses + (from -> status))
    }
  }

  /**
   * Maybe the id to be given to the next event to be sent to `address`
   * and the updated job status.
   *
   * The id is `None` if an acknowledgment has already been received
   * for the potential next id. Meaning that the event for which the
   * id is being generated does not have to be sent as it was already
   * receive via another sender.
   *
   * @param address the address for which the generate an id.
   * @return the updated job status and potential id.
   */
  private def nextId(address: Address): (JobStatus, Option[Long]) = {
    val status = statuses.getOrElse(address, JobStatus(Map.empty, Set.empty))

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
      (status, Some(potentialNextId))
    }
  }
}

object Notifier {
  def apply[E](send: (E, Address, Long) => SyncIO[Cancellable]): Notifier[E] = new Notifier(Map.empty, send)

  final case class JobStatus(jobs: Map[Long, Cancellable], stashedAcks: Set[Long])
}
