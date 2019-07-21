package com.swissborg.sbr
package strategy
package keepoldest

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepOldestSpec5MultiJvmNode1 extends KeepOldestSpec5
class KeepOldestSpec5MultiJvmNode2 extends KeepOldestSpec5
class KeepOldestSpec5MultiJvmNode3 extends KeepOldestSpec5
class KeepOldestSpec5MultiJvmNode4 extends KeepOldestSpec5
class KeepOldestSpec5MultiJvmNode5 extends KeepOldestSpec5

/**
  * Node4 and node5 are indirectly connected in a five node cluster
  *
  * Node4 and node5 should down themselves as they are indirectly connected.
  * The three other nodes survive as they can reach the oldest.
  */
sealed abstract class KeepOldestSpec5
    extends FiveNodeSpec("KeepOldest", KeepOldestSpecFiveNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 5" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node4, node5, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3) {
        waitForSurvivors(node1, node2, node3)
        waitForAllLeaving(node4, node5)
      }

      runOn(node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
