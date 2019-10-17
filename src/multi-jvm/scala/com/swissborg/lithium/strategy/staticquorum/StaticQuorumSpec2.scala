package com.swissborg.lithium

package strategy

package staticquorum

import akka.remote.transport.ThrottlerTransportAdapter.Direction

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
sealed abstract class StaticQuorumSpec2 extends FiveNodeSpec("StaticQuorum", StaticQuorumSpec2Config) {
  override def assertions(): Unit =
    "handle scenario 2" in within(60 seconds) {
      runOn(node1) {
        com.swissborg.lithium.TestUtil
          .linksToKillForPartitions(List(node1, node2, node3) :: List(node4) :: List(node5) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3) {
        waitForSurvivors(node1, node2, node3)
        waitForAllLeaving(node4, node5)
      }

      runOn(node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
