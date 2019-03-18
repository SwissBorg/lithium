package test

import akka.actor.Address
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction
import akka.testkit.ImplicitSender
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import test.util.{STMultiNodeSpec, StaticQuorumSpecConfig}

import scala.concurrent.duration._

class StaticQuorumSpecMultiJvmNode1 extends StaticQuorumSpec
class StaticQuorumSpecMultiJvmNode2 extends StaticQuorumSpec
class StaticQuorumSpecMultiJvmNode3 extends StaticQuorumSpec

class StaticQuorumSpec
    extends MultiNodeSpec(StaticQuorumSpecConfig)
    with STMultiNodeSpec
    with ImplicitSender
    with Eventually
    with IntegrationPatience {
  import StaticQuorumSpecConfig._

  override def initialParticipants: Int = roles.size

  private val addresses: Map[RoleName, Address]      = roles.map(r => r -> node(r).address).toMap
  private def addressOf(roleName: RoleName): Address = addresses(roleName)

  "An ActorInspectorManager" - {
    "1 - Start the 1st node" in {
      runOn(node1) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1)
      }

      enterBarrier("node1-up")
    }

    "2 - Start the 2nd node" in {
      runOn(node2) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2)
      }

      enterBarrier("node2-up")
    }

    "3 - Start the 3rd node" in {
      runOn(node3) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3)
      }

      enterBarrier("node3-up")
    }

    "4 - Bidirectional link failure" in {
      runOn(node1) {
        // Kill link bi-directionally to node3
        testConductor.blackhole(node1, node3, Direction.Both).await
        testConductor.blackhole(node2, node3, Direction.Both).await
      }

      enterBarrier("node3-disconnected")

      runOn(node1, node2) {
        waitForUp(node1, node2)
        waitToBecomeUnreachable(node3)
      }

      enterBarrier("node3-unreachable")

      runOn(node1, node2) {
        waitForUnreachableHandling()
        waitForSurvivors(node1, node2)
      }

      enterBarrier("node3-downed")
    }
  }

  private def waitToBecomeUnreachable(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond(
      Cluster(system).state.unreachable.exists(_.address == address),
      30 seconds
    )
  }

  private def waitForUnreachableHandling(): Unit =
    awaitCond(Cluster(system).state.unreachable.isEmpty, 30 seconds)

  private def waitForSurvivors(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond(Cluster(system).state.members.exists(_.address == address), 30 seconds)
  }

  private def waitForUp(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond(Cluster(system).state.members.exists(m => m.address == address && m.status == Up), 30 seconds)
  }
}
