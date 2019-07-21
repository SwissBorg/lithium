package com.swissborg.sbr
package strategy
package multidc

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TestUtil.linksToKillForPartitions

import scala.concurrent.duration._

class MultiDcSpecMultiJvmNode1 extends MultiDcSpec
class MultiDcSpecMultiJvmNode2 extends MultiDcSpec
class MultiDcSpecMultiJvmNode3 extends MultiDcSpec
class MultiDcSpecMultiJvmNode4 extends MultiDcSpec
class MultiDcSpecMultiJvmNode5 extends MultiDcSpec

/**
  * Tests that only the network partition between node1-2 and node3 is resolved.
  * The others shouldn't since they are cross-dc.
  */
sealed abstract class MultiDcSpec extends FiveNodeSpec("MultiDc", MultiDcSpecConfig) {
  override def assertions(): Unit =
    "handle scenario 1" in within(60 seconds) {
      runOn(node1) {
        linksToKillForPartitions(List(node1, node2) :: List(node3) :: List(node4, node5) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }
      }

      enterBarrier("links-failed")

      runOn(node1, node2) {
        waitForSurvivors(node1, node2)
        waitForAllLeaving(node3)
      }

      runOn(node4, node5) {
        waitForSurvivors(node4, node5)
      }

      runOn(node3) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
