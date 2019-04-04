package akka.cluster.sbr

import akka.actor.{ActorSystem, Address}
import akka.cluster.Cluster
import akka.cluster.MemberStatus.{Down, Up}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import scala.concurrent.duration._

abstract class ThreeNodeSpec(name: String, config: ThreeNodeSpecConfig)
    extends MultiNodeSpec(config)
    with STMultiNodeSpec
    with ImplicitSender
    with Eventually
    with IntegrationPatience {

  def assertions(): Unit

  final protected val node1 = config.node1
  final protected val node2 = config.node2
  final protected val node3 = config.node3

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

    assertions()
  }

  private val addresses: Map[RoleName, Address] = roles.map(r => r -> node(r).address).toMap

  addresses.foreach(println)

  private def addressOf(roleName: RoleName): Address = addresses(roleName)

  protected def waitToBecomeUnreachable(roleNames: RoleName*): Unit =
    awaitCond(
      roleNames.map(addressOf).forall(address => Cluster(system).state.unreachable.exists(_.address === address))
    )

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
    awaitCond {
//      println(s"BLA: ${Cluster(system).state.members.find(_.address === selfAddress)}")
      Cluster(system).state.members.exists(m => m.address === selfAddress && m.status === Down)
    }
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
