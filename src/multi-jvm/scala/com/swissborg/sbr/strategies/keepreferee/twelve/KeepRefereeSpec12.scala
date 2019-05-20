package com.swissborg.sbr.strategies.keepreferee.twelve

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.TestUtil.linksToKillForPartitions
import com.swissborg.sbr.strategies.keepreferee.KeepRefereeSpecFiveNodeLessNodesConfig

import scala.concurrent.duration._

class KeepRefereeSpec12MultiJvmNode1 extends KeepRefereeSpec12
class KeepRefereeSpec12MultiJvmNode2 extends KeepRefereeSpec12
class KeepRefereeSpec12MultiJvmNode3 extends KeepRefereeSpec12
class KeepRefereeSpec12MultiJvmNode4 extends KeepRefereeSpec12
class KeepRefereeSpec12MultiJvmNode5 extends KeepRefereeSpec12

/**
 * (1) Partition containing node1 and node2, node3, and node4 (node3 and node4 are indirectly connected)
 * (2) Partition containing  node5
 *
 * (1) contains the referee but has less than down-all-if-less-than-nodes so downs itself.
 * (2) downs itself as it doesn't contain the referee.
 */
class KeepRefereeSpec12 extends FiveNodeSpec("StaticQuorum", KeepRefereeSpecFiveNodeLessNodesConfig) {
  override def assertions(): Unit =
    "handle when the remaining partition is too small with indirectly connected nodes" in within(120 seconds) {
      runOn(node1) {
        linksToKillForPartitions(
          List(List(node1, node2, node3, node4), List(node5))
        ).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }

        testConductor.blackhole(node3, node4, Direction.Both).await
      }

      enterBarrier("links-disconnected")

      runOn(node1, node2, node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
