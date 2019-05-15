package com.swissborg.sbr.strategies.keepmajority.four

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.ThreeNodeSpec
import com.swissborg.sbr.strategies.keepmajority.KeepMajoritySpecThreeNodeConfig

import scala.concurrent.duration._

class KeepMajoritySpec4MultiJvmNode1 extends KeepMajoritySpec4
class KeepMajoritySpec4MultiJvmNode2 extends KeepMajoritySpec4
class KeepMajoritySpec4MultiJvmNode3 extends KeepMajoritySpec4

class KeepMajoritySpec4 extends ThreeNodeSpec("KeepMajority", KeepMajoritySpecThreeNodeConfig) {
  override def assertions(): Unit =
    "Unidirectional link failure" in within(120 seconds) {
      runOn(node1) {
        // Node2 cannot receive node3 messages
        val _ = testConductor.blackhole(node2, node3, Direction.Receive).await
      }

      enterBarrier("node3-disconnected")

      runOn(node1, node2) {
        waitForUp(node1, node2)
      }

      enterBarrier("node3-unreachable")

      enterBarrier("node1-3-up")

      runOn(node2) {
        waitForUp(node2)
        waitToBecomeUnreachable(node3)
      }

      runOn(node1) {
        waitToBecomeUnreachable(node2, node3)
      }

      enterBarrier("node3-unreachable")

      runOn(node2, node3) {
        waitForSelfDowning
      }

      enterBarrier("node-2-3-suicide")

      runOn(node1) {
        waitForDownOrGone(node2, node3)
      }

      enterBarrier("done")
    }
}
