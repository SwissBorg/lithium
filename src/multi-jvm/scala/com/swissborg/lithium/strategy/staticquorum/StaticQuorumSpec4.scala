package com.swissborg.lithium

package strategy

package staticquorum

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class StaticQuorumSpec4MultiJvmNode1 extends StaticQuorumSpec4
class StaticQuorumSpec4MultiJvmNode2 extends StaticQuorumSpec4
class StaticQuorumSpec4MultiJvmNode3 extends StaticQuorumSpec4

/**
  * Node2 and node3 are indirectly connected in a three node cluster.
  *
  * Node2 and node3 should down themselves as they are indirectly connected.
  * Node1 should down itself since its not a quorum.
  */
sealed abstract class StaticQuorumSpec4 extends ThreeNodeSpec("StaticQuorum", StaticQuorumSpecConfig) {
  override def assertions(): Unit =
    "handle scenario 4" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node2, node3, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3) {
        waitForSelfDowning
      }

      enterBarrier("node1-2-3-suicide")
    }
}
