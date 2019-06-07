package com.swissborg.sbr.strategy.keepreferee.eleven

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.TestUtil.linksToKillForPartitions
import com.swissborg.sbr.strategy.keepreferee.KeepRefereeSpecFiveNodeLessNodesConfig

import scala.concurrent.duration._

class KeepRefereeSpec11MultiJvmNode1 extends KeepRefereeSpec11
class KeepRefereeSpec11MultiJvmNode2 extends KeepRefereeSpec11
class KeepRefereeSpec11MultiJvmNode3 extends KeepRefereeSpec11
class KeepRefereeSpec11MultiJvmNode4 extends KeepRefereeSpec11
class KeepRefereeSpec11MultiJvmNode5 extends KeepRefereeSpec11

/**
 * (1) Partition containing node1 and node2
 * (2) Partition containing node3, node4, and node5
 *
 * (1) contains the referee but has less than down-all-if-less-than-nodes so downs itself.
 * (2) downs itself as it doesn't contain the referee.
 */
class KeepRefereeSpec11 extends FiveNodeSpec("KeepReferee", KeepRefereeSpecFiveNodeLessNodesConfig) {
  override def assertions(): Unit =
    "handle scenario 11" in within(120 seconds) {
      runOn(node1) {
        linksToKillForPartitions(
          List(List(node1, node2), List(node3, node4, node5))
        ).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
