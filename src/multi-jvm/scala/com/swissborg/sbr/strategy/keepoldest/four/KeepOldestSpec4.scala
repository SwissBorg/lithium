package com.swissborg.sbr.strategy.keepoldest.four

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.ThreeNodeSpec
import com.swissborg.sbr.strategy.keepoldest.KeepOldestSpecThreeNodeConfig

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
    "handle scenario 4" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node1, node2, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node3) {
        waitForAllLeaving(node1, node2)
      }

      runOn(node1, node2) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
