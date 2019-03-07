package akka.sbr

import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.Member
import akka.cluster.MemberStatus.WeaklyUp

import scala.collection.immutable.SortedSet

trait ClusterState[S] {
  def reachableNodes(s: S): SortedSet[Member]
  def unreachableNodes(s: S): Set[Member]
}

object ClusterState {
  def apply[A](implicit ev: ClusterState[A]): ClusterState[A] = ev

  implicit val akkaClusterState: ClusterState[CurrentClusterState] = new ClusterState[CurrentClusterState] {
    override def reachableNodes(s: CurrentClusterState): SortedSet[Member] =
      s.members
        .diff(s.unreachable)
        .filterNot(_.status == WeaklyUp) // nodes on the other side do not know about the `WeaklyUp` members

    override def unreachableNodes(s: CurrentClusterState): Set[Member] = s.unreachable
  }
}
