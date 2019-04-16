package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Cancellable, Props, Stash}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.sbr.implicits._
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Actor reporting the reachability status of cluster members from its own point
 * of view. Essentially, it adds the [[IndirectlyConnectedNode]] status to the
 * reachability events.
 *
 * A node is indirectly connected when its local failure detector does not have
 * the same opinion as the reachability information of the cluster. This can
 * happen when a link between two nodes is faulty and cannot communicate
 * anymore. In this case the two nodes see each other as unreachable (which
 * will be gossiped) but could still reach other by taking a different path.
 *
 * The actor will once a second compare its reachability information
 * with the one gossiped and update the `parent` accordingly. As a result,
 * it introduces a one second delay compared to normal reachability events.
 *
 * Similarly to the reachability events coming from the underlying failure detector
 * this one is also eventually consisted.
 *
 * @param parent the actor to report to.
 * @param cluster the cluster membership information.
 */
class SBRFailureDetector(parent: ActorRef, cluster: Cluster) extends Actor with ActorLogging with Stash {
  import SBRFailureDetector._

  /* --- State --- */

  // Sends a `Opinion` message to itself.
  private var opinion: Option[Cancellable] = None

  // For each address, what the has been gossiped (true when it is reachable) and
  // if it was locally reachable at the time of the previous "opinion" check.
  private var lastOpinions: Map[Address, (Boolean, Boolean)] = Map.empty

  override def receive: Receive = waitingForState

  def waitingForState: Receive = {
    case CurrentClusterState(members, unreachable, _, _, _) =>
      // Initial setup of the opinions. The first opinions will be sent
      // out after receiving the first `Opinion` event.

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

  def main: Receive = {
    case Opinion =>
      cluster.state.members.foreach { member =>
        if (member =!= selfMember && fDetector.isMonitoring(member.address)) {
          val localAvailability = fDetector.isAvailable(member.address)

          /**
           * Runs `f` when the local availabilty has changed since the last "opinion" check.
           */
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

              // There's a high probability that the member is really unreachable but
              // hasn't been detected yet as such from the current cluster member. If
              // that's the case it should be eventually corrected.
              parent ! IndirectlyConnectedNode(member)
            } else {
              parent ! UnreachableMember(member)
            }

            false

          case ReachableMember(member) =>
            if (!isLocallyAvailable) {
              log.debug("INIT REA: {}", IndirectlyConnectedNode(member))

              // There's a high probability that the member is really unreachable but
              // hasn't been detected yet as such from the current cluster member. If
              // that's the case it should be eventually corrected.
              parent ! IndirectlyConnectedNode(member)
            } else {
              parent ! ReachableMember(member)
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

object SBRFailureDetector {
  def props(parent: ActorRef, cluster: Cluster): Props = Props(new SBRFailureDetector(parent, cluster))

  final case class Opinion(i: Int)

  /* --- Constants for code documentation --- */

  final private val isReachable   = true
  final private val isUnreachable = false

  final private val isLocallyAvailable   = true
  final private val isLocallyUnavailable = false

  /* -----------------------------------------*/
}
