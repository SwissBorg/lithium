package com.swissborg.sbr
package strategy
package keepoldest

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TestUtil.linksToKillForPartitions

import scala.concurrent.duration._

class KeepOldestSpec6MultiJvmNode1 extends KeepOldestSpec6
class KeepOldestSpec6MultiJvmNode2 extends KeepOldestSpec6
class KeepOldestSpec6MultiJvmNode3 extends KeepOldestSpec6
class KeepOldestSpec6MultiJvmNode4 extends KeepOldestSpec6
class KeepOldestSpec6MultiJvmNode5 extends KeepOldestSpec6

/**
  * The link between node2 and node4 fails.
  * The link between node3 and node5 fails.
  *
  * All nodes but node1 downs itself because they are indirectly connected.
  * Node1 survives as its the oldest node.
  */
sealed abstract class KeepOldestSpec6
    extends FiveNodeSpec("KeepOldest", KeepOldestSpecFiveNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 6" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node2, node4, Direction.Receive).await
        testConductor.blackhole(node3, node5, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1) {
        waitForAllLeaving(node2, node3, node4, node5)
      }

      runOn(node2, node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
