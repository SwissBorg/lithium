package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Cancellable, Props, Stash}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.sbr.implicits._
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class IndirectConnectionReporter(parent: ActorRef, cluster: Cluster) extends Actor with ActorLogging with Stash {
  import IndirectConnectionReporter._

  /* --- State --- */
  private var opinion: Option[Cancellable]                   = None
  private var lastOpinions: Map[Address, (Boolean, Boolean)] = Map.empty

  override def receive: Receive = waitingForState

  def waitingForState: Receive = {
    case CurrentClusterState(members, unreachable, _, _, _) =>
      val unreachableLastOpinions: Map[Address, (Boolean, Boolean)] =
        unreachable.flatMap { member =>
          if (fDetector.isMonitoring(member.address)) {
            Some(member.address -> ((false, fDetector.isAvailable(member.address))))
          } else None
        }(collection.breakOut)

      val reachableLastOpinions: Map[Address, (Boolean, Boolean)] = (members -- unreachable).flatMap { member =>
        if (fDetector.isMonitoring(member.address)) {
          Some(member.address -> ((true, fDetector.isAvailable(member.address))))
        } else None
      }(collection.breakOut)

      opinion = scheduleOpinion()
      lastOpinions = unreachableLastOpinions ++ reachableLastOpinions

      unstashAll()
      context.become(main)

    case _ => stash()
  }

  // isReachable, isLocallyReachable
  def main: Receive = {
    case Opinion =>
      cluster.state.members.foreach { member =>
        if (member =!= selfMember && fDetector.isMonitoring(member.address)) {
          val localAvailability = fDetector.isAvailable(member.address)

          def onChange(prevLocalAvailability: Boolean)(f: => Unit): Unit =
            if (prevLocalAvailability != localAvailability) {
              f

              // We can get safely get the KV pair as `onChange` is only called
              // when `lastOpinions.contains(member.address)`
              lastOpinions = lastOpinions + (member.address -> ((lastOpinions(member.address)._1, localAvailability)))
            }

          (lastOpinions.get(member.address), localAvailability) match {
            case (Some((`isReachable`, prevLocalAvailability)), `isLocallyUnavailable`) =>
              onChange(prevLocalAvailability) {
                log.debug("UPDATE: {}", IndirectlyConnectedNode(member))
                parent ! IndirectlyConnectedNode(member)
              }

            case (Some((`isReachable`, prevLocalAvailability)), `isLocallyAvailable`) =>
              onChange(prevLocalAvailability) {
                log.debug("UPDATE: {}", ReachableMember(member))
                parent ! ReachableMember(member)
              }

            case (Some((`isUnreachable`, prevLocalAvailability)), `isLocallyAvailable`) =>
              onChange(prevLocalAvailability) {
                log.debug("UPDATE: {}", IndirectlyConnectedNode(member))
                parent ! IndirectlyConnectedNode(member)
              }

            case (Some((`isUnreachable`, prevLocalAvailability)), `isLocallyUnavailable`) =>
              onChange(prevLocalAvailability) {
                log.debug("UPDATE: {}", UnreachableMember(member))
                parent ! UnreachableMember(member)
              }

            case _ => ()
          }
        }
      }

      opinion = scheduleOpinion()

    case e: ReachabilityEvent =>
      if (fDetector.isMonitoring(e.member.address)) {
        val isLocallyAvailable = fDetector.isAvailable(e.member.address)

        val isReachable = e match {
          case UnreachableMember(member) =>
            if (isLocallyAvailable) {
              log.debug("INIT UNR: {}", IndirectlyConnectedNode(member))
              parent ! IndirectlyConnectedNode(member)
            }
            false

          case ReachableMember(member) =>
            if (!isLocallyAvailable) {
              log.debug("INIT REA: {}", IndirectlyConnectedNode(member))
              parent ! IndirectlyConnectedNode(member)
            }
            true
        }

        lastOpinions = lastOpinions + (e.member.address -> ((isReachable, isLocallyAvailable)))
      }

    case MemberRemoved(member, _) =>
      log.debug("Removing {} from the opinions.", member)
      lastOpinions = lastOpinions - member.address
  }

  def scheduleOpinion(): Some[Cancellable] =
    Some(context.system.scheduler.scheduleOnce(1.second, self, Opinion))

  private val selfMember = cluster.selfMember
  private val fDetector  = cluster.failureDetector

  override def preStart(): Unit =
    cluster.subscribe(self,
                      InitialStateAsSnapshot,
                      classOf[MemberRemoved],
                      classOf[akka.cluster.ClusterEvent.ReachabilityEvent])

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    opinion.foreach(_.cancel())
  }

  implicit private val ec: ExecutionContext = context.dispatcher
}

object IndirectConnectionReporter {
  def props(parent: ActorRef, cluster: Cluster): Props = Props(new IndirectConnectionReporter(parent, cluster))

  final case class Opinion(i: Int)

  /* --- Constants for code documentation --- */

  final private val isReachable   = true
  final private val isUnreachable = false

  final private val isLocallyAvailable   = true
  final private val isLocallyUnavailable = false

  /* -----------------------------------------*/
}
