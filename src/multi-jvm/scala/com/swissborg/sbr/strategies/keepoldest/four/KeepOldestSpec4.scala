package com.swissborg.sbr.strategies.keepoldest.four

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.ThreeNodeSpec
import com.swissborg.sbr.strategies.keepoldest.KeepOldestSpecThreeNodeConfig

import scala.concurrent.duration._

class KeepOldestSpec4MultiJvmNode1 extends KeepOldestSpec4
class KeepOldestSpec4MultiJvmNode2 extends KeepOldestSpec4
class KeepOldestSpec4MultiJvmNode3 extends KeepOldestSpec4

/**
 * Node1 and node2 are indirectly connected in a three node cluster
 *
 * Node1 and node2 should down themselves as they are indirectly connected.
 * Node3 should down itself as its not the oldest.
 */
class KeepOldestSpec4 extends ThreeNodeSpec("KeepOldest", KeepOldestSpecThreeNodeConfig) {
  override def assertions(): Unit =
    "handle indirectly-connected nodes" in within(120 seconds) {
      runOn(node1) {
        val _ = testConductor.blackhole(node1, node2, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1) {
        waitToBecomeUnreachable(node2)
      }

      runOn(node2) {
        waitToBecomeUnreachable(node1)
      }

      runOn(node3) {
        waitToBecomeUnreachable(node1, node2)
      }

      enterBarrier("split-brain")

      runOn(node3) {
        waitForDownOrGone(node1, node2)
      }

      runOn(node1, node2) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
