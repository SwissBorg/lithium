package com.swissborg.lithium

package strategy

package keepmajority

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepMajoritySpec6MultiJvmNode1 extends KeepMajoritySpec6
class KeepMajoritySpec6MultiJvmNode2 extends KeepMajoritySpec6
class KeepMajoritySpec6MultiJvmNode3 extends KeepMajoritySpec6
class KeepMajoritySpec6MultiJvmNode4 extends KeepMajoritySpec6
class KeepMajoritySpec6MultiJvmNode5 extends KeepMajoritySpec6

/**
  * The link between node2 and node4 fails.
  * The link between node3 and node5 fails.
  *
  * All nodes but node1 downs itself because they are indirectly connected.
  * Node1 survives as it is a majority (only node left).
  */
sealed abstract class KeepMajoritySpec6 extends FiveNodeSpec("KeepMajority", KeepMajoritySpecFiveNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 6" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node2, node4, Direction.Receive).await
        testConductor.blackhole(node3, node5, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node2, node3, node4, node5) {
        waitForSelfDowning
      }

      runOn(node1) {
        waitForAllLeaving(node2, node3, node4, node5)
      }

      enterBarrier("split-brain-resolved")
    }
}
