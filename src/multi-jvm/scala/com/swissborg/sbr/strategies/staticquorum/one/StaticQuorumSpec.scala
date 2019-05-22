package com.swissborg.sbr.strategies.staticquorum.one

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.ThreeNodeSpec
import com.swissborg.sbr.strategies.staticquorum.StaticQuorumSpecConfig

import scala.concurrent.duration._

class StaticQuorumSpecMultiJvmNode1 extends StaticQuorumSpec
class StaticQuorumSpecMultiJvmNode2 extends StaticQuorumSpec
class StaticQuorumSpecMultiJvmNode3 extends StaticQuorumSpec

/**
 * Creates the partitions:
 *   (1) node1, node2
 *   (2) node3
 *
 * (1) should survive as it is a quorum.
 * (2) should down itself as it is not a quorum.
 */
class StaticQuorumSpec extends ThreeNodeSpec("StaticQuorum", StaticQuorumSpecConfig) {
  override def assertions(): Unit =
    "Bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Partition with node1 and node 2 <- survive
        // Partition with node 3           <- killed
        com.swissborg.sbr.TestUtil.linksToKillForPartitions(List(node1, node2) :: List(node3) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node1, node2) {
        waitForSurvivors(node1, node2)
        waitForDownOrGone(node3)
      }

      runOn(node3) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
