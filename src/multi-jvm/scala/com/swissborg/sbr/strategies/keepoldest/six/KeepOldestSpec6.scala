package com.swissborg.sbr.strategies.keepoldest.six

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.strategies.keepoldest.KeepOldestSpecFiveNodeConfig

import scala.concurrent.duration._

class KeepOldestSpec6MultiJvmNode1 extends KeepOldestSpec6
class KeepOldestSpec6MultiJvmNode2 extends KeepOldestSpec6
class KeepOldestSpec6MultiJvmNode3 extends KeepOldestSpec6
class KeepOldestSpec6MultiJvmNode4 extends KeepOldestSpec6
class KeepOldestSpec6MultiJvmNode5 extends KeepOldestSpec6

/**
 * The link between node1 and node4 fails.
 * The link between node3 and node5 fails.
 *
 * All nodes but node2 downs itself because they are indirectly connected.
 * Node2 survives as its the oldest node.
 */
class KeepOldestSpec6 extends FiveNodeSpec("KeepOldest", KeepOldestSpecFiveNodeConfig) {
  override def assertions(): Unit =
    "Unidirectional link failure" in within(120 seconds) {
      runOn(node1) {
        // Node4 cannot receive node5 messages
        val a = testConductor.blackhole(node1, node4, Direction.Receive).await
        val b = testConductor.blackhole(node3, node5, Direction.Receive).await
      }

      enterBarrier("nodes-disconnected")

      runOn(node1) {
        waitForUp(node1)
        waitToBecomeUnreachable(node4)
      }

      enterBarrier("node4-unreachable")

      runOn(node4) {
        waitForUp(node4)
        waitToBecomeUnreachable(node1)
      }

      enterBarrier("node1-unreachable")

      ///

      runOn(node3) {
        waitForUp(node3)
        waitToBecomeUnreachable(node5)
      }

      enterBarrier("node5-unreachable")

      runOn(node5) {
        waitForUp(node5)
        waitToBecomeUnreachable(node3)
      }

      enterBarrier("node3-unreachable")

      ///

      runOn(node1, node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
