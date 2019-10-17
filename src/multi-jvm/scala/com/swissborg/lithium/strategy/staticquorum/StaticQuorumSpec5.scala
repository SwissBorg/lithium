package com.swissborg.lithium

package strategy

package staticquorum

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class StaticQuorumSpec5MultiJvmNode1 extends StaticQuorumSpec5
class StaticQuorumSpec5MultiJvmNode2 extends StaticQuorumSpec5
class StaticQuorumSpec5MultiJvmNode3 extends StaticQuorumSpec5
class StaticQuorumSpec5MultiJvmNode4 extends StaticQuorumSpec5
class StaticQuorumSpec5MultiJvmNode5 extends StaticQuorumSpec5

/**
 * Node4 and node5 are indirectly connected in a five node cluster
 *
 * Node4 and node5 should down themselves as they are indirectly connected.
 * The three other nodes survive as they form a quorum.
 */
sealed abstract class StaticQuorumSpec5 extends FiveNodeSpec("StaticQuorum", StaticQuorumSpec2Config) {
  override def assertions(): Unit =
    "handle scenario 5" in within(60 seconds) {
      runOn(node1) {
        // Node4 cannot receive node5 messages
        testConductor.blackhole(node4, node5, Direction.Receive).await
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
