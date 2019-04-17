package akka.cluster.sbr

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member, UniqueAddress}

/**
 * Actor reporting the reachability status of cluster members based on
 * `akka.cluster.Reachability`. Essentially, it adds the [[IndirectlyConnectedNode]]
 * status to the reachability events.
 *
 * A node is indirectly connected when all the observers do not have
 * the same opinion. This can happen when a link between two nodes is
 * faulty and cannot communicate anymore. In this case the two nodes
 * see each other as unreachable (which will be gossiped) but could
 * still reach other by taking a different path.
 *
 * This failure detector is eventually consistent.
 *
 * @param parent the actor to report to.
 * @param cluster the cluster membership information.
 */
class SBRFailureDetector(parent: ActorRef, cluster: Cluster) extends Actor with ActorLogging with Stash {
  import SBRFailureDetector._

  override def receive: Receive = main(Map.empty)

  def main(lastSBRReachabilities: Map[UniqueAddress, SBRReachability]): Receive = {
    case ReachabilityChanged(r) =>
      def sendIfChangedSinceLast(reachability: SBRReachability, member: Member): Unit = {
        lastSBRReachabilities.get(member.uniqueAddress) match {
          case Some(`reachability`) => ()
          case _ =>
            parent ! (reachability match {
              case Reachable           => ReachableMember(member)
              case Unreachable         => UnreachableMember(member)
              case IndirectlyConnected => IndirectlyConnectedNode(member)
            })
        }
        context.become(main(lastSBRReachabilities + (member.uniqueAddress -> reachability)))
      }

      val nObservers = r.allObservers.size

      r.observersGroupedByUnreachable.foreach {
        case (memberUniqueAddress, unreachableFrom) =>
          val unreachableFromN = unreachableFrom.size
          lazy val member      = cluster.state.members.find(_.uniqueAddress == memberUniqueAddress).get

          if (unreachableFromN <= 0) {
            sendIfChangedSinceLast(Reachable, member)
          } else if (unreachableFromN > 0 && unreachableFromN < nObservers) {
            sendIfChangedSinceLast(IndirectlyConnected, member)
          } else {
            sendIfChangedSinceLast(Unreachable, member)
          }
      }

    // todo never called
    case MemberRemoved(member, _) =>
      log.debug("Removing {} from the opinions.", member)
      context.become(main(lastSBRReachabilities - member.uniqueAddress))
  }

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[ReachabilityChanged])
    context.system.eventStream.subscribe(self, classOf[MemberRemoved])
  }

  override def postStop(): Unit = context.system.eventStream.unsubscribe(self)
}

object SBRFailureDetector {
  def props(parent: ActorRef, cluster: Cluster): Props = Props(new SBRFailureDetector(parent, cluster))

  sealed abstract class SBRReachability extends Product with Serializable
  final case object Reachable           extends SBRReachability
  final case object Unreachable         extends SBRReachability
  final case object IndirectlyConnected extends SBRReachability
}
