package akka.cluster.sbr

import akka.actor.{ActorContext, ActorRef, Address, Cancellable}
import akka.cluster.ClusterEvent.{ReachabilityEvent, ReachableMember, UnreachableMember}
import akka.cluster.Member
import akka.cluster.sbr.Downer.{SecondGuessReachableRequest, SecondGuessUnreachableRequest, Snitch}
import akka.cluster.sbr.Snitches.Work

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

final case class Snitches(private val triggers: Map[Address, Work], private val publish: (String, Snitch) => Unit) {

  def snitch(
    reachabilityEvent: ReachabilityEvent,
  )(implicit context: ActorContext, ec: ExecutionContext, self: ActorRef): Snitches = reachabilityEvent match {
    case UnreachableMember(member) => secondGuessUnreachable(member, publish)
    case ReachableMember(member)   => secondGuessReachable(member, publish)
  }

  /**
   * Schedules a second guess
   */
  private def secondGuessUnreachable(
    member: Member,
    f: (String, SecondGuessUnreachableRequest) => Unit
  )(implicit context: ActorContext, ec: ExecutionContext, self: ActorRef): Snitches = {
    val address = member.address

    val ackN0 = triggers.get(address).fold(0L) {
      case Work(ackN, cancellable) =>
        cancellable.cancel()
        ackN + 1
    }

    copy(
      triggers = triggers + (address ->
        Work(ackN0,
             context.system.scheduler
               .schedule(0.seconds, 1.second)(f(member.address, SecondGuessUnreachableRequest(member, ackN0)))))
    )
  }

  private def secondGuessReachable(
    member: Member,
    f: (String, SecondGuessReachableRequest) => Unit
  )(implicit context: ActorContext, ec: ExecutionContext, self: ActorRef): Snitches = {
    val address = member.address

    val ackN0 = triggers.get(address).fold(0L) {
      case Work(ackN, cancellable) =>
        cancellable.cancel()
        ackN + 1
    }

    copy(
      triggers = triggers + (address ->
        Work(ackN0,
             context.system.scheduler
               .schedule(0.seconds, 1.second)(f(member.address, SecondGuessReachableRequest(member, ackN0)))))
    )
  }

  /**
   * Response to the second guess over `member`.
   */
  def response(member: Member, ackN: Int): Snitches = {
    val address = member.address

    triggers.get(address).foreach {
      case Work(ackN0, cancellable) =>
        if (ackN0 == ackN) {
          cancellable.cancel()
        } else {
          // stale ack
        }
    }

    copy(triggers = triggers - address)
  }

  /**
   * Cancels the second guessing of `member`.
   */
  def cancel(member: Member): Snitches = {
    val address = member.address
    triggers.get(address).foreach(_.cancellable.cancel())
    copy(triggers = triggers - address)
  }

  def cancelAll(): Snitches = {
    triggers.values.foreach(_.cancellable.cancel())
    copy(triggers = Map.empty)
  }
}

object Snitches {
  final case class Work(ackN: Long, cancellable: Cancellable)

  def apply(publish: (String, Snitch) => Unit): Snitches = Snitches(Map.empty, publish)
}
