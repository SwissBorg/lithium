package com.swissborg.lithium

package strategy

package multidc

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.lithium.TestUtil.linksToKillForPartitions

import scala.concurrent.duration._

class MultiDcSpecMultiJvmNode1  extends MultiDcSpec
class MultiDcSpecMultiJvmNode2  extends MultiDcSpec
class MultiDcSpecMultiJvmNode3  extends MultiDcSpec
class MultiDcSpecMultiJvmNode4  extends MultiDcSpec
class MultiDcSpecMultiJvmNode5  extends MultiDcSpec
class MultiDcSpecMultiJvmNode6  extends MultiDcSpec
class MultiDcSpecMultiJvmNode7  extends MultiDcSpec
class MultiDcSpecMultiJvmNode8  extends MultiDcSpec
class MultiDcSpecMultiJvmNode9  extends MultiDcSpec
class MultiDcSpecMultiJvmNode10 extends MultiDcSpec

/**
  * Tests that only the network partition between node1-2 and node3 is resolved.
  * The others shouldn't since they are cross-dc.
  */
sealed abstract class MultiDcSpec extends TenNodeSpec("MultiDc", MultiDcSpecConfig) {
  override def assertions(): Unit =
    "handle scenario 1" in within(120 seconds) {
      runOn(node1) {
        linksToKillForPartitions(
          List(node1, node2, node3) :: List(node4, node5) :: List(node6, node7, node8, node9, node10) :: Nil
        ).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3) {
        waitForSurvivors(node1, node2, node3)
        waitForAllLeaving(node4, node5)
      }

      runOn(node6, node7, node8, node9, node10) {
        waitForSurvivors(node6, node7, node8, node9, node10)
      }

      runOn(node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
