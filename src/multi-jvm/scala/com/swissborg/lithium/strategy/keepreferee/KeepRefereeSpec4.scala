package com.swissborg.lithium

package strategy

package keepreferee

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepRefereeSpec4MultiJvmNode1 extends KeepRefereeSpec4
class KeepRefereeSpec4MultiJvmNode2 extends KeepRefereeSpec4
class KeepRefereeSpec4MultiJvmNode3 extends KeepRefereeSpec4
class KeepRefereeSpec4MultiJvmNode4 extends KeepRefereeSpec4
class KeepRefereeSpec4MultiJvmNode5 extends KeepRefereeSpec4

/**
 * Node4 and node5 are indirectly connected in a five node cluster
 *
 * Node4 and node5 should down themselves as they are indirectly connected.
 * The three other nodes survive as they can reach the referee.
 */
sealed abstract class KeepRefereeSpec4 extends FiveNodeSpec("KeepReferee", KeepRefereeSpecFiveNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 4" in within(120 seconds) {
      runOn(node1) {
        // Node4 cannot receive node5 messages
        // Node4 and node5 will shutdown because they are indirectly connected.
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
