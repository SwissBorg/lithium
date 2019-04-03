package akka.cluster.sbr

import akka.actor.{ActorContext, ActorRef, Address, Cancellable}
import akka.cluster.ClusterEvent.{ReachabilityEvent, ReachableMember, UnreachableMember}
import akka.cluster.{Cluster, Member}
import akka.cluster.sbr.Downer.{Snitch, SnitchRequest}
import akka.cluster.sbr.Snitches.Work
import cats.effect.SyncIO
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

final case class Snitches(private val triggers: Map[Address, Work],
                          private val publish: (String, Snitch) => SyncIO[Unit]) {

  def snitch(
    reachabilityEvent: ReachabilityEvent,
    cluster: Cluster
  )(implicit context: ActorContext, ec: ExecutionContext, self: ActorRef): SyncIO[Snitches] = {
    val address = reachabilityEvent.member.address

    if (cluster.failureDetector.isAvailable(address)) {
      for {
        ackN0 <- nextAckN(address)
        cancellable <- SyncIO(
          context.system.scheduler
            .schedule(0.seconds, 1.second)(
              publish(address.toString, SnitchRequest(reachabilityEvent, self, ackN0)).unsafeRunSync()
            )
        )
      } yield copy(triggers = triggers + (address -> Work(ackN0, cancellable)))
    } else {
      SyncIO.pure(this)
    }
  }

  /**
   * Response to the second guess over `member`.
   */
  def response(member: Member, ackN: Long): SyncIO[Snitches] = {
    val address = member.address

    val cancel = triggers
      .get(address)
      .traverse {
        case Work(ackN0, cancellable) =>
          if (ackN0 == ackN) {
            println("CANCELLED")
            SyncIO(cancellable.cancel()).void
          } else {
            println("FAILED TO CANCEL")
            SyncIO.unit
          }
      }

    cancel.as(copy(triggers = triggers - address))
  }

  /**
   * Cancels the second guessing of `member`.
   */
  def cancel(member: Member): SyncIO[Snitches] = {
    val address = member.address
    val cancel  = triggers.get(address).traverse(c => SyncIO(c.cancellable.cancel()))
    cancel.as(copy(triggers = triggers - address))
  }

  def cancelAll(): SyncIO[Snitches] = {
    val cancel = triggers.values.toList.traverse(c => SyncIO(c.cancellable.cancel()))
    cancel.as(copy(triggers = Map.empty))
  }

  private def nextAckN(address: Address): SyncIO[Long] = triggers.get(address).fold(SyncIO.pure(0L)) {
    case Work(ackN, cancellable) => SyncIO(cancellable.cancel()).as(ackN + 1)
  }
}

object Snitches {
  final case class Work(ackN: Long, cancellable: Cancellable)

  def apply(publish: (String, Snitch) => SyncIO[Unit]): Snitches = Snitches(Map.empty, publish)
}
