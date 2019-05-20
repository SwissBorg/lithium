package com.swissborg.sbr.strategies.keepoldest.twelve

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.TestUtil.linksToKillForPartitions
import com.swissborg.sbr.strategies.keepoldest.RoleKeepOldestSpecDownAloneConfig

import scala.concurrent.duration._

class KeepOldestSpec12MultiJvmNode1 extends KeepOldestSpec12
class KeepOldestSpec12MultiJvmNode2 extends KeepOldestSpec12
class KeepOldestSpec12MultiJvmNode3 extends KeepOldestSpec12
class KeepOldestSpec12MultiJvmNode4 extends KeepOldestSpec12
class KeepOldestSpec12MultiJvmNode5 extends KeepOldestSpec12

/**
 * Creates the partitions:
 *   (1) node1, node2
 *   (2) node3, node4, node5 (link between node3 and node4 broken)
 *
 * (1) should down itself because the oldest node (node2) is alone (with the given role).
 * (2) only node5 should survive as node3 and node4 and indirectly connected.
 */
class KeepOldestSpec12 extends FiveNodeSpec("KeepOldest", RoleKeepOldestSpecDownAloneConfig) {
  override def assertions(): Unit =
    "down the partition with the oldest node when alone with an unclean network partition" in within(60 seconds) {
      runOn(node1) {
        linksToKillForPartitions(List(node1, node2) :: List(node3, node4, node5) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }

        testConductor.blackhole(node3, node4, Direction.Both).await
      }

      enterBarrier("links-failed")

      runOn(node5) {
        waitToBecomeUnreachable(node1, node2)
      }

      runOn(node3) {
        waitToBecomeUnreachable(node1, node2, node4)
      }

      runOn(node4) {
        waitToBecomeUnreachable(node1, node2, node3)
      }

      runOn(node1, node2) {
        waitToBecomeUnreachable(node3, node4, node5)
      }

      enterBarrier("split-brain")

      runOn(node5) {
        waitForDownOrGone(node1, node2, node3, node4)
      }

      runOn(node1, node2, node3, node4) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
