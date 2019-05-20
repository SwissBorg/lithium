package com.swissborg.sbr.strategies.keepoldest.eleven

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.strategies.keepoldest.RoleKeepOldestSpecDownAloneConfig

import scala.concurrent.duration._

class KeepOldestSpec11MultiJvmNode1 extends KeepOldestSpec11
class KeepOldestSpec11MultiJvmNode2 extends KeepOldestSpec11
class KeepOldestSpec11MultiJvmNode3 extends KeepOldestSpec11
class KeepOldestSpec11MultiJvmNode4 extends KeepOldestSpec11
class KeepOldestSpec11MultiJvmNode5 extends KeepOldestSpec11

/**
 * Creates the partitions:
 *   (1) node1, node2
 *   (2) node3, node4, node5
 *
 * (1) should down itself because the oldest node (node2) is alone (with the given role).
 * (2) should survive as the oldest node is alone.
 */
class KeepOldestSpec11 extends FiveNodeSpec("KeepOldest", RoleKeepOldestSpecDownAloneConfig) {
  override def assertions(): Unit =
    "down the partition with the oldest node when alone" in within(60 seconds) {
      runOn(node1) {
        com.swissborg.sbr.TestUtil
          .linksToKillForPartitions(List(node1, node2) :: List(node3, node4, node5) :: Nil)
          .foreach {
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

      runOn(node3, node4, node5) {
        waitForDownOrGone(node1, node2)
        waitForSurvivors(node3, node4, node5)
      }

      enterBarrier("node3-4-5-downed")

      runOn(node1, node2) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
