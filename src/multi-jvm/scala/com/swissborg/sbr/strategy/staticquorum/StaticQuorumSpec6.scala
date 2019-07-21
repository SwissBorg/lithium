package com.swissborg.sbr
package strategy
package staticquorum

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class StaticQuorumSpec6MultiJvmNode1 extends StaticQuorumSpec6
class StaticQuorumSpec6MultiJvmNode2 extends StaticQuorumSpec6
class StaticQuorumSpec6MultiJvmNode3 extends StaticQuorumSpec6
class StaticQuorumSpec6MultiJvmNode4 extends StaticQuorumSpec6
class StaticQuorumSpec6MultiJvmNode5 extends StaticQuorumSpec6

/**
  * The link between node1 and node4 fails.
  * The link between node3 and node5 fails.
  *
  * All nodes but node2 downs itself because they are indirectly connected.
  * Node2 downs itself as it does not form a quorum.
  */
sealed abstract class StaticQuorumSpec6
    extends FiveNodeSpec("StaticQuorum", StaticQuorumSpec2Config) {
  override def assertions(): Unit =
    "handle scenario 6" in within(120 seconds) {
      runOn(node1) {
        // Node4 cannot receive node5 messages
        testConductor.blackhole(node1, node4, Direction.Receive).await
        testConductor.blackhole(node3, node5, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
