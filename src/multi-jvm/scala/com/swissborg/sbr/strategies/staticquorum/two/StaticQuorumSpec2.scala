package com.swissborg.sbr.strategies.staticquorum.two

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.strategies.staticquorum.StaticQuorumSpec2Config

import scala.concurrent.duration._

class StaticQuorumSpec2MultiJvmNode1 extends StaticQuorumSpec2
class StaticQuorumSpec2MultiJvmNode2 extends StaticQuorumSpec2
class StaticQuorumSpec2MultiJvmNode3 extends StaticQuorumSpec2
class StaticQuorumSpec2MultiJvmNode4 extends StaticQuorumSpec2
class StaticQuorumSpec2MultiJvmNode5 extends StaticQuorumSpec2

/**
 * Creates the partitions:
 *   (1) node1, node2, node3
 *   (2) node4
 *   (3) node5
 *
 * (1) should survive as it is a quorum.
 * (2) should down itself as it is not a quorum.
 * (3) should down itself as it is not a quorum.
 */
class StaticQuorumSpec2 extends FiveNodeSpec("StaticQuorum", StaticQuorumSpec2Config) {
  override def assertions(): Unit =
    "Three partitions, bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Partition of node1, node2, node3 <- survives
        // Partition of node 4              <- killed
        // Partition of node 5              <- killed
        com.swissborg.sbr.TestUtil
          .linksToKillForPartitions(List(node1, node2, node3) :: List(node4) :: List(node5) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3) {
        waitToBecomeUnreachable(node4, node5)
      }

      runOn(node4) {
        waitToBecomeUnreachable(node1, node2, node3, node5)
      }

      runOn(node5) {
        waitToBecomeUnreachable(node1, node2, node3, node4)
      }

      enterBarrier("split-brain")

      runOn(node1, node2, node3) {
        waitForSurvivors(node1, node2, node3)
        waitForDownOrGone(node4, node5)
      }

      runOn(node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
