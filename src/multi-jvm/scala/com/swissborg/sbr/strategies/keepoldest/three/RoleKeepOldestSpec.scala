package com.swissborg.sbr.strategies.keepoldest.three

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.strategies.keepoldest.RoleKeepOldestSpecConfig

import scala.concurrent.duration._

class RoleKeepOldestSpecMultiJvmNode1 extends RoleKeepOldestSpec
class RoleKeepOldestSpecMultiJvmNode2 extends RoleKeepOldestSpec
class RoleKeepOldestSpecMultiJvmNode3 extends RoleKeepOldestSpec
class RoleKeepOldestSpecMultiJvmNode4 extends RoleKeepOldestSpec
class RoleKeepOldestSpecMultiJvmNode5 extends RoleKeepOldestSpec

/**
 * Creates the partitions:
 *   (1) node1, node2
 *   (2) node3, node4, node5
 *
 * (1) should survive as it contains the oldest node within the given role.
 * (2) should down itself as it does not contain the oldest node within the given role.
 */
class RoleKeepOldestSpec extends FiveNodeSpec("KeepOldest", RoleKeepOldestSpecConfig) {
  override def assertions(): Unit =
    "Bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Partition with node1 and node2          <- survive (contains oldest node given role)
        // Partition with node3, node4, and node 5 <- killed
        com.swissborg.sbr.TestUtil.linksToKillForPartitions(List(node1, node2) :: List(node3, node4, node5) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node3, node4, node5) {
        waitForUp(node3, node4, node5)
        waitToBecomeUnreachable(node1, node2)
      }

      enterBarrier("node1-2-unreachable")

      runOn(node1, node2) {
        waitForUp(node1, node2)
        waitToBecomeUnreachable(node3, node4, node5)
      }

      enterBarrier("node-3-4-5-unreachable")

      runOn(node1, node2) {
        waitForSurvivors(node1, node2)
        waitForDownOrGone(node3, node4, node5)
      }

      enterBarrier("node3-4-5-downed")

      runOn(node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("node3-4-5-suicide")
    }
}
