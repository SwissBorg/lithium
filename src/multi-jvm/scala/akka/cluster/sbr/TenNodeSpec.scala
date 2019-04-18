package akka.cluster.sbr

import akka.actor.{ActorSystem, Address}
import akka.cluster.Cluster
import akka.cluster.MemberStatus.{Down, Up}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import scala.concurrent.duration._

abstract class TenNodeSpec(name: String, config: TenNodeSpecConfig)
    extends MultiNodeSpec(config)
    with STMultiNodeSpec
    with ImplicitSender
    with Eventually
    with IntegrationPatience {

  def assertions(): Unit

  protected val node1: RoleName  = config.node1
  protected val node2: RoleName  = config.node2
  protected val node3: RoleName  = config.node3
  protected val node4: RoleName  = config.node4
  protected val node5: RoleName  = config.node5
  protected val node6: RoleName  = config.node6
  protected val node7: RoleName  = config.node7
  protected val node8: RoleName  = config.node8
  protected val node9: RoleName  = config.node9
  protected val node10: RoleName = config.node10

  override def initialParticipants: Int = roles.size

  s"$name" - {
    "Start the 1st node" in within(30 seconds) {
      runOn(node1) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1)
      }

      enterBarrier("node1-up")
    }

    "Start the 2nd node" in within(30 seconds) {
      runOn(node2) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2)
      }

      enterBarrier("node2-up")
    }

    "Start the 3rd node" in within(30 seconds) {
      runOn(node3) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3)
      }

      enterBarrier("node3-up")
    }

    "Start the 4th node" in within(30 seconds) {
      runOn(node4) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4)
      }

      enterBarrier("node4-up")
    }

    "Start the 5th node" in within(30 seconds) {
      runOn(node5) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5)
      }

      enterBarrier("node5-up")
    }

    "Start the 6th node" in within(30 seconds) {
      runOn(node6) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node6)
      }

      enterBarrier("node6-up")
    }

    "Start the 7th node" in within(30 seconds) {
      runOn(node7) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node7)
      }

      enterBarrier("node7-up")
    }

    "Start the 8th node" in within(30 seconds) {
      runOn(node8) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node7, node8)
      }

      enterBarrier("node8-up")
    }

    "Start the 9th node" in within(30 seconds) {
      runOn(node9) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node7, node8, node9)
      }

      enterBarrier("node9-up")
    }

    "Start the 10th node" in within(30 seconds) {
      runOn(node10) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node7, node8, node9, node10)
      }

      enterBarrier("node10-up")
    }

    assertions()
  }

  private val addresses: Map[RoleName, Address] = roles.map(r => r -> node(r).address).toMap
  addresses.foreach(a => log.debug(s"$a"))

  private def addressOf(roleName: RoleName): Address = addresses(roleName)

  protected def waitToBecomeUnreachable(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond(Cluster(system).state.unreachable.exists(_.address === address))
  }

  protected def waitForUnreachableHandling(): Unit =
    awaitCond(Cluster(system).state.unreachable.isEmpty)

  protected def waitForSurvivors(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond(Cluster(system).state.members.exists(_.address === address))
  }

  protected def waitForUp(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond(Cluster(system).state.members.exists(m => m.address === address && m.status === Up))
  }

  protected def waitForSelfDowning(implicit system: ActorSystem): Unit = {
    val selfAddress = Cluster(system).selfAddress
    awaitCond(Cluster(system).state.members.exists(m => m.address === selfAddress && m.status === Down))
  }

  protected def waitForDownOrGone(roleNames: RoleName*): Unit = roleNames.map(addressOf).foreach { address =>
    awaitCond {
      val members     = Cluster(system).state.members
      val unreachable = Cluster(system).state.unreachable

      unreachable.isEmpty &&                                              // no unreachable members
      (members.exists(m => m.address === address && m.status === Down) || // member is down
      !members.exists(_.address === address)) // member is not in the cluster
    }
  }
}
