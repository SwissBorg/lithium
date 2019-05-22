package com.swissborg.sbr.strategies.keepreferee.five

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.strategies.keepreferee.KeepRefereeSpecFiveNodeConfig

import scala.concurrent.duration._

class KeepRefereeSpec5MultiJvmNode1 extends KeepRefereeSpec5
class KeepRefereeSpec5MultiJvmNode2 extends KeepRefereeSpec5
class KeepRefereeSpec5MultiJvmNode3 extends KeepRefereeSpec5
class KeepRefereeSpec5MultiJvmNode4 extends KeepRefereeSpec5
class KeepRefereeSpec5MultiJvmNode5 extends KeepRefereeSpec5

/**
 * Node4 and node5 are indirectly connected in a five node cluster
 *
 * Node4 and node5 should down themselves as they are indirectly connected.
 * The three other nodes survive as they contain the referee.
 */
class KeepRefereeSpec5 extends FiveNodeSpec("KeepReferee", KeepRefereeSpecFiveNodeConfig) {
  override def assertions(): Unit =
    "handle indirectly connected nodes" in within(60 seconds) {
      runOn(node1) {
        // Node4 cannot receive node5 messages
        testConductor.blackhole(node4, node5, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node4) {
        waitToBecomeUnreachable(node5)
      }

      runOn(node5) {
        waitToBecomeUnreachable(node4)
      }

      runOn(node1, node2, node3) {
        waitToBecomeUnreachable(node4, node5)
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
