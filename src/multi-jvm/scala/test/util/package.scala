package test

import akka.actor.ActorSystem
import akka.cluster.MemberStatus.WeaklyUp
import akka.cluster.{Cluster, Member}
import akka.remote.testconductor.RoleName

package object util {
  def unreachableFrom(system: ActorSystem): Set[Member] = Cluster.get(system).state.unreachable

  def reachableFrom(system: ActorSystem): Set[Member] = {
    val state = Cluster.get(system).state

    state.members
      .diff(state.unreachable)
      .filterNot(_.status == WeaklyUp)
  }
}
