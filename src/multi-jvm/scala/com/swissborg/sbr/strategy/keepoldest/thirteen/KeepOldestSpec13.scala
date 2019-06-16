package com.swissborg.sbr.strategy.keepoldest.thirteen

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.TestUtil.linksToKillForPartitions
import com.swissborg.sbr.strategy.keepoldest.RoleKeepOldestSpecDownAloneConfig

import scala.concurrent.duration._

class KeepOldestSpec13MultiJvmNode1 extends KeepOldestSpec13
class KeepOldestSpec13MultiJvmNode2 extends KeepOldestSpec13
class KeepOldestSpec13MultiJvmNode3 extends KeepOldestSpec13
class KeepOldestSpec13MultiJvmNode4 extends KeepOldestSpec13
class KeepOldestSpec13MultiJvmNode5 extends KeepOldestSpec13

/**
  * Creates the partitions:
  *   (1) node1, node2, node3, node4 (link between node3 and node4 broken)
  *   (2) node5
  *
  * Node5 is not aware of the fact that node3 and node4 are indirectly connected so doesn't
  * see the oldest node (node2) alone. On the other hand, node2, from his point of view, is alone
  * (based on the role and the fact that node3 and node4 are IC). Partition (2) will down itself and
  * partition (1) will to not down itself (even if the oldest is alone) so that the cluster doesn't
  * go down.
  */
class KeepOldestSpec13 extends FiveNodeSpec("KeepOldest", RoleKeepOldestSpecDownAloneConfig) {
  override def assertions(): Unit =
    "handle scenario 13" in within(60 seconds) {
      runOn(node1) {
        linksToKillForPartitions(List(node1, node2, node3, node4) :: List(node5) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }

        testConductor.blackhole(node3, node4, Direction.Both).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2) {
        waitForDownOrGone(node3, node4, node5)
      }

      runOn(node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
