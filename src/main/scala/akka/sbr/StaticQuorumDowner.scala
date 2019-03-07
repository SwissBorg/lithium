package akka.sbr

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, UnreachableMember}

import scala.concurrent.duration.FiniteDuration

class StaticQuorumDowner(cluster: Cluster, quorumSize: QuorumSize, stableAfter: FiniteDuration)
    extends Actor
    with ActorLogging {

  override def receive: Receive = {
    case UnreachableMember(_) =>
  }

  def handle(): Unit =
    ReachableNodeGroup(cluster.state, quorumSize)
      .map { reachableNodeGroup =>
        Decision
          .staticQuorum(reachableNodeGroup, UnreachableNodeGroup(cluster.state, quorumSize))
          .addressesToDown
          .foreach(cluster.down)
      }
      .fold(err => {
        log.error(s"Oh fuck... $err")
        throw new IllegalStateException(s"Oh fuck... $err")
      }, identity)

//
//  val a: MemberStatus = a match {
//    case MemberStatus.Joining  =>
//    case MemberStatus.WeaklyUp =>
//    case MemberStatus.Up       =>
//    case MemberStatus.Leaving  =>
//    case MemberStatus.Exiting  =>
//    case MemberStatus.Down     =>
//    case MemberStatus.Removed  =>
//  }

  override def preStart(): Unit =
    cluster.subscribe(self, InitialStateAsEvents, classOf[UnreachableMember])

  override def postStop(): Unit =
    cluster.unsubscribe(self)
}

object StaticQuorumDowner {
  def props(cluster: Cluster, quorumSize: QuorumSize, stableAfter: FiniteDuration): Props =
    Props(new StaticQuorumDowner(cluster, quorumSize, stableAfter))
}
