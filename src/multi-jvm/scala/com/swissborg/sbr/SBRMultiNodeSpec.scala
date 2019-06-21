package com.swissborg.sbr

import akka.actor.{ActorSystem, Address}
import akka.cluster.Cluster
import akka.cluster.MemberStatus._
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec, MultiNodeSpecCallbacks}
import akka.testkit.ImplicitSender
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

abstract class SBRMultiNodeSpec(val config: MultiNodeConfig)
    extends MultiNodeSpec(config)
    with MultiNodeSpecCallbacks
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender
    with Eventually
    with IntegrationPatience {
  override def beforeAll(): Unit = multiNodeSpecBeforeAll()
  override def afterAll(): Unit = multiNodeSpecAfterAll()

  private val addresses: Map[RoleName, Address] = roles.map(r => r -> node(r).address).toMap

  protected def addressOf(roleName: RoleName): Address = addresses(roleName)

  protected def waitToBecomeUnreachable(roleNames: RoleName*): Unit =
    awaitCond(allUnreachable(roleNames: _*))

  protected def waitForSurvivors(roleNames: RoleName*): Unit =
    awaitCond(allSurvivors(roleNames: _*))

  protected def waitForUp(roleNames: RoleName*): Unit = awaitCond(allUp(roleNames: _*))

  protected def waitForSelfDowning(implicit system: ActorSystem): Unit = awaitCond(downedItself)

  protected def waitForAllLeaving(roleNames: RoleName*): Unit =
    awaitCond(allLeaving(roleNames: _*))

  protected def waitExistsAllDownOrGone(groups: Seq[Seq[RoleName]]): Unit =
    awaitCond(existsAllDownOrGone(groups))

  private def allUnreachable(roleNames: RoleName*): Boolean =
    roleNames.forall(
      role => Cluster(system).state.unreachable.exists(_.address === addressOf(role))
    )

  private def allSurvivors(roleNames: RoleName*): Boolean =
    roleNames.forall(role => Cluster(system).state.members.exists(_.address === addressOf(role)))

  private def allUp(roleNames: RoleName*): Boolean =
    roleNames.forall(
      role =>
        Cluster(system).state.members.exists(m => m.address === addressOf(role) && m.status === Up)
    )

  private def existsAllDownOrGone(groups: Seq[Seq[RoleName]]): Boolean =
    groups.exists(group => allLeaving(group: _*))

  private def downedItself(implicit system: ActorSystem): Boolean = {
    val selfAddress = Cluster(system).selfAddress
//    println(s"------------- ${Cluster(system).state.unreachable}")
    Cluster(system).state.members
      .exists(m => m.address === selfAddress && (m.status === Exiting || m.status === Down))
  }

  private def allLeaving(roleNames: RoleName*): Boolean =
    roleNames.forall { role =>
      val members = Cluster(system).state.members
      val unreachable = Cluster(system).state.unreachable

      val address = addressOf(role)

      unreachable.isEmpty && // no unreachable members
      (members.exists(m => m.address === address && (m.status === Down || m.status === Exiting)) || // member is down
      !members.exists(_.address === address)) // member is not in the cluster
    }
}
