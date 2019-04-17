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
  private var lastSBRReachabilities: Map[Address, SBRReachability] = Map.empty

  override def receive: Receive = waitingForState

  def waitingForState: Receive = {
    case CurrentClusterState(members, unreachable, seenBy, _, _) =>
      val indirectlyConnectedNodes = unreachable.filter(m => seenBy.contains(m.address))
      val unreachableNodes         = unreachable -- indirectlyConnectedNodes
      val reachableNodes           = members -- unreachable

      indirectlyConnectedNodes.foreach(m => parent ! IndirectlyConnectedNode(m))
      unreachableNodes.foreach(m => parent ! UnreachableMember(m))
      reachableNodes.foreach(m => parent ! ReachableMember(m))

      lastSBRReachabilities = (indirectlyConnectedNodes.map(m => m.address -> IndirectlyConnected) ++
        unreachableNodes.map(m => m.address                                -> Unreachable) ++
        reachableNodes.map(m => m.address                                  -> Reachable)).toMap

      unstashAll()
      opinion = scheduleOpinion()
      context.become(main)

    case _ => stash()
  }

  def main: Receive = {
    case Opinion =>
      log.debug("OPINION")

      val indirectlyConnectedNodes = cluster.state.unreachable.filter(m => cluster.state.seenBy.contains(m.address))
      indirectlyConnectedNodes.foreach { m =>
        lastSBRReachabilities.get(m.address).foreach {
          case IndirectlyConnected => ()
          case _                   => parent ! IndirectlyConnectedNode(m)
        }

        lastSBRReachabilities += (m.address -> IndirectlyConnected)
      }

      val unreachableNodes = cluster.state.unreachable -- indirectlyConnectedNodes
      unreachableNodes.foreach { m =>
        lastSBRReachabilities.get(m.address).foreach {
          case Unreachable => ()
          case _           => parent ! UnreachableMember(m)
        }

        lastSBRReachabilities += (m.address -> Unreachable)
      }

      val reachableNodes = cluster.state.members -- cluster.state.unreachable
      reachableNodes.foreach { m =>
        lastSBRReachabilities.get(m.address).foreach {
          case Reachable => ()
          case _         => parent ! ReachableMember(m)
        }

        lastSBRReachabilities += (m.address -> Reachable)
      }

      val (availableMembers, unavailableMembers) = cluster.state.members.iterator
        .filter(m => m != selfMember && fDetector.isMonitoring(m.address))
        .partition(m => fDetector.isAvailable(m.address))

      if (availableMembers.nonEmpty && unavailableMembers.nonEmpty) {
        parent ! IndirectlyConnectedNode()
      }

      opinion = scheduleOpinion()

    case e: ReachabilityEvent =>
      // First blindly trust the reachability event
      // as its highly probable that it is correct.
      // If it's a mistake it will be corrected in a
      // subsequent "opinion" step.

      e match {
        case UnreachableMember(member) =>
          lastSBRReachabilities += (member.address -> Unreachable)

        case ReachableMember(member) =>
          lastSBRReachabilities += (member.address -> Reachable)
      }

      parent ! e

    case MemberRemoved(member, _) =>
      log.debug("Removing {} from the opinions.", member)
      lastSBRReachabilities = lastSBRReachabilities - member.address
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

  sealed abstract class SBRReachability extends Product with Serializable
  final case object Reachable           extends SBRReachability
  final case object Unreachable         extends SBRReachability
  final case object IndirectlyConnected extends SBRReachability

  /* --- Constants for code documentation --- */

  final private val isReachable   = true
  final private val isUnreachable = false

  final private val isLocallyAvailable   = true
  final private val isLocallyUnavailable = false

  /* -----------------------------------------*/
}
