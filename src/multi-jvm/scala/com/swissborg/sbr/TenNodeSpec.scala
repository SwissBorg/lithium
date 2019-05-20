package com.swissborg.sbr

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

  s"$name" must {
    "start node-1" in within(30 seconds) {
      runOn(node1) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1)
      }

      enterBarrier("node-1-up")
    }

    "start node-2" in within(30 seconds) {
      runOn(node2) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2)
      }

      enterBarrier("node-2-up")
    }

    "start node-3" in within(30 seconds) {
      runOn(node3) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3)
      }

      enterBarrier("node-3-up")
    }

    "start node-4" in within(30 seconds) {
      runOn(node4) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4)
      }

      enterBarrier("node-4-up")
    }

    "start node-5" in within(30 seconds) {
      runOn(node5) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5)
      }

      enterBarrier("node-5-up")
    }

    "start node-6" in within(30 seconds) {
      runOn(node6) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node6)
      }

      enterBarrier("node-6-up")
    }

    "start node-7" in within(30 seconds) {
      runOn(node7) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node6, node7)
      }

      enterBarrier("node-7-up")
    }

    "start node-8" in within(30 seconds) {
      runOn(node8) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node6, node7, node8)
      }

      enterBarrier("node-8-up")
    }

    "start node-9" in within(30 seconds) {
      runOn(node9) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node6, node7, node8, node9)
      }

      enterBarrier("node-9-up")
    }

    "start node-10" in within(30 seconds) {
      runOn(node10) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5, node6, node7, node8, node9, node10)
      }

      enterBarrier("node-10-up")
    }

    assertions()
  }

  private val addresses: Map[RoleName, Address] = roles.map(r => r -> node(r).address).toMap
  addresses.foreach(a => log.debug(s"$a"))

  private def addressOf(roleName: RoleName): Address = addresses(roleName)

  protected def waitToBecomeUnreachable(roleNames: RoleName*): Unit       = awaitCond(allUnreachable(roleNames: _*))
  protected def waitForUnreachableHandling(): Unit                        = awaitCond(Cluster(system).state.unreachable.isEmpty)
  protected def waitForSurvivors(roleNames: RoleName*): Unit              = awaitCond(allSurvivors(roleNames: _*))
  protected def waitForUp(roleNames: RoleName*): Unit                     = awaitCond(allUp(roleNames: _*))
  protected def waitAllButOneUp(roleNames: RoleName*): Unit               = awaitCond(allButOneUp(roleNames: _*))
  protected def waitForSelfDowning(implicit system: ActorSystem): Unit    = awaitCond(downedItself)
  protected def waitForDownOrGone(roleNames: RoleName*): Unit             = awaitCond(allDownOrGone(roleNames: _*))
  protected def waitAllButOneDownOrGone(roleNames: RoleName*): Unit       = awaitCond(allButOneDownOrGone(roleNames: _*))
  protected def waitExistsAllDownOrGone(groups: Seq[Seq[RoleName]]): Unit = awaitCond(existsAllDownOrGone(groups))

  private def allUnreachable(roleNames: RoleName*): Boolean =
    roleNames.forall(role => Cluster(system).state.unreachable.exists(_.address === addressOf(role)))

  private def allSurvivors(roleNames: RoleName*): Boolean =
    roleNames.forall(role => Cluster(system).state.members.exists(_.address === addressOf(role)))

  private def allUp(roleNames: RoleName*): Boolean =
    roleNames.forall(
      role => Cluster(system).state.members.exists(m => m.address === addressOf(role) && m.status === Up)
    )

  private def allButOneUp(roleNames: RoleName*): Boolean = {
    val s = roleNames.toSet
    roleNames.combinations(2).exists(rs => allUp(rs: _*) && allDownOrGone((s -- rs).toSeq: _*))
  }

  private def existsAllDownOrGone(groups: Seq[Seq[RoleName]]): Boolean =
    groups.exists(group => allDownOrGone(group: _*))

  private def allButOneDownOrGone(roleNames: RoleName*): Boolean = {
    val s = roleNames.toSet
    roleNames.combinations(2).exists(rs => allDownOrGone(rs: _*) && allUp((s -- rs).toSeq: _*))
  }

  private def downedItself(implicit system: ActorSystem): Boolean = {
    val selfAddress = Cluster(system).selfAddress
    Cluster(system).state.members.exists(m => m.address === selfAddress && m.status === Down)
  }

  private def allDownOrGone(roleNames: RoleName*): Boolean =
    roleNames.forall { role =>
      val members     = Cluster(system).state.members
      val unreachable = Cluster(system).state.unreachable

      val address = addressOf(role)
      unreachable.isEmpty &&                                              // no unreachable members
      (members.exists(m => m.address === address && m.status === Down) || // member is down
      !members.exists(_.address === address)) // member is not in the cluster
    }
}
