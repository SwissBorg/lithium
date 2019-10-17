package com.swissborg.lithium

package strategy

package multidc

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.lithium.TestUtil.linksToKillForPartitions

import scala.concurrent.duration._

class MultiDcSpec2MultiJvmNode1  extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode2  extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode3  extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode4  extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode5  extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode6  extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode7  extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode8  extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode9  extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode10 extends MultiDcSpec2

/**
 * Tests that the network partition between node1-2 and node3 is resolved and
 * the one between node4 and node5 but nothing cross-dc.
 */
sealed abstract class MultiDcSpec2 extends TenNodeSpec("MultiDc", MultiDcSpecConfig) {
  override def assertions(): Unit =
    "handle scenario 2" in within(120 seconds) {
      runOn(node1) {
        linksToKillForPartitions(
          List(node1, node2, node3) :: List(node4, node5) :: List(node6, node7) :: List(node8, node9, node10) :: Nil
        ).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3) {
        waitForSurvivors(node1, node2, node3)
        waitForAllLeaving(node4, node5)
      }

      runOn(node8, node9, node10) {
        waitForSurvivors(node8, node9, node10)
        waitForAllLeaving(node6, node7)
      }

      runOn(node4, node5, node6, node7) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
