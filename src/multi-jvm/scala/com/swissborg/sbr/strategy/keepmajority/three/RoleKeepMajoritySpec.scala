package com.swissborg.sbr.strategy.keepmajority.three

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.TestUtil.linksToKillForPartitions
import com.swissborg.sbr.strategy.keepmajority.RoleKeepMajoritySpecConfig

import scala.concurrent.duration._

class RoleKeepMajoritySpecMultiJvmNode1 extends RoleKeepMajoritySpec
class RoleKeepMajoritySpecMultiJvmNode2 extends RoleKeepMajoritySpec
class RoleKeepMajoritySpecMultiJvmNode3 extends RoleKeepMajoritySpec
class RoleKeepMajoritySpecMultiJvmNode4 extends RoleKeepMajoritySpec
class RoleKeepMajoritySpecMultiJvmNode5 extends RoleKeepMajoritySpec

class RoleKeepMajoritySpec extends FiveNodeSpec("KeepMajority", RoleKeepMajoritySpecConfig) {
  override def assertions(): Unit =
    "handle scenario 3" in within(60 seconds) {
      runOn(node1) {
        linksToKillForPartitions(List(List(node1, node2), List(node3, node4, node5))).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node3, node4, node5) {
        waitToBecomeUnreachable(node1, node2)
      }

      runOn(node1, node2) {
        waitToBecomeUnreachable(node3, node4, node5)
      }

      enterBarrier("split-brain")

      runOn(node1, node2) {
        waitForSurvivors(node1, node2)
        waitForAllLeaving(node3, node4, node5)
      }

      runOn(node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
