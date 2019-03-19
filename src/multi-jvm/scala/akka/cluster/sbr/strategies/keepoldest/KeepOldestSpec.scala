package akka.cluster.sbr.strategies.keepoldest

import akka.actor.Address
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.cluster.sbr.STMultiNodeSpec
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction
import akka.testkit.ImplicitSender
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import scala.concurrent.duration._

class KeepOldestSpecMultiJvmNode1 extends KeepOldestSpec
class KeepOldestSpecMultiJvmNode2 extends KeepOldestSpec
class KeepOldestSpecMultiJvmNode3 extends KeepOldestSpec

class KeepOldestSpec
    extends MultiNodeSpec(KeepOldestSpecConfig)
    with STMultiNodeSpec
    with ImplicitSender
    with Eventually
    with IntegrationPatience {
  import KeepOldestSpecConfig._

  override def initialParticipants: Int = roles.size

  private val addresses: Map[RoleName, Address]      = roles.map(r => r -> node(r).address).toMap
  private def addressOf(roleName: RoleName): Address = addresses(roleName)

  addresses.foreach(println)

  "KeepOldest" - {
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

    "4 - Bidirectional link failure" in within(30 seconds) {
      runOn(node1) {
        // Kill link to node1
        testConductor.blackhole(node2, node1, Direction.Both).await
        testConductor.blackhole(node3, node1, Direction.Both).await
      }

      enterBarrier("node1-disconnected")

      runOn(node1) {
        waitForUp(node1)
        waitToBecomeUnreachable(node2, node3)
      }

      enterBarrier("node2-3-unreachable")

      runOn(node1) {
        waitForUnreachableHandling()
        waitForSurvivors(node1)
      }

      enterBarrier("node2-3-downed")
    }
  }

  private def waitToBecomeUnreachable(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond(Cluster(system).state.unreachable.exists(_.address == address))
  }

  private def waitForUnreachableHandling(): Unit =
    awaitCond(Cluster(system).state.unreachable.isEmpty)

  private def waitForSurvivors(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond(Cluster(system).state.members.exists(_.address == address))
  }

  private def waitForUp(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond(Cluster(system).state.members.exists(m => m.address == address && m.status == Up))
  }
}
