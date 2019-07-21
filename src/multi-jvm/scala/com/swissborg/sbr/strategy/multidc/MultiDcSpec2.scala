package com.swissborg.sbr
package strategy
package multidc

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TestUtil.linksToKillForPartitions

import scala.concurrent.duration._

class MultiDcSpec2MultiJvmNode1 extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode2 extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode3 extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode4 extends MultiDcSpec2
class MultiDcSpec2MultiJvmNode5 extends MultiDcSpec2

/**
  * Tests that the network partition between node1-2 and node3 is resolved and
  * the one between node4 and node5 but nothing cross-dc.
  */
sealed abstract class MultiDcSpec2 extends FiveNodeSpec("MultiDc", MultiDcSpecConfig) {
  override def assertions(): Unit =
    "handle scenario 2" in within(60 seconds) {
      runOn(node1) {
        linksToKillForPartitions(
          List(node1, node2) :: List(node3) :: List(node4) :: List(node5) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }
      }

      enterBarrier("links-failed")

      runOn(node1, node2) {
        waitForSurvivors(node1, node2)
        waitForAllLeaving(node3)
      }

      runOn(node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
