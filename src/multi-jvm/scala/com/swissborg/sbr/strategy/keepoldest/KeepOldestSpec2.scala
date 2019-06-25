package com.swissborg.sbr
package strategy
package keepoldest

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TestUtil.linksToKillForPartitions

import scala.concurrent.duration._

class KeepOldestSpec2MultiJvmNode1 extends KeepOldestSpec2
class KeepOldestSpec2MultiJvmNode2 extends KeepOldestSpec2
class KeepOldestSpec2MultiJvmNode3 extends KeepOldestSpec2
class KeepOldestSpec2MultiJvmNode4 extends KeepOldestSpec2
class KeepOldestSpec2MultiJvmNode5 extends KeepOldestSpec2

/**
  * Creates the partitions:
  *   (1) node1
  *   (2) node2, node3
  *   (3) node4, node5
  *
  * (1) should survive as it contains the oldest.
  * (2) should down itself as it does not contain the oldest.
  * (3) should down itself as it does not contain the oldest.
  */
class KeepOldestSpec2 extends FiveNodeSpec("KeepOldest", KeepOldestSpecFiveNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 2" in within(120 seconds) {
      runOn(node1) {
        linksToKillForPartitions(List(node1) :: List(node2, node3) :: List(node4, node5) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }
      }

      enterBarrier("link-failed")

      runOn(node1) {
        waitForSurvivors(node1)
        waitForAllLeaving(node2, node3, node4, node5)
      }

      runOn(node2, node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
