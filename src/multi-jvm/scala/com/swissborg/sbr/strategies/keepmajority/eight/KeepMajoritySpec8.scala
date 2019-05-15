package com.swissborg.sbr.strategies.keepmajority.eight

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TenNodeSpec
import com.swissborg.sbr.strategies.keepmajority.KeepMajoritySpecTenNodeConfig

import scala.concurrent.duration._

class KeepMajoritySpec8MultiJvmNode1  extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode2  extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode3  extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode4  extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode5  extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode6  extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode7  extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode8  extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode9  extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode10 extends KeepMajority8

/**
 * Node9 and node10 are indirectly connected in a ten node cluster
 */
class KeepMajority8 extends TenNodeSpec("StaticQuorum", KeepMajoritySpecTenNodeConfig) {
  override def assertions(): Unit =
    "Split-brain" in within(120 seconds) {
      runOn(node1) {
        // Node9 cannot receive node10 messages
        val a = testConductor.blackhole(node9, node10, Direction.Receive).await
        val b = testConductor.blackhole(node2, node3, Direction.Receive).await
      }

      enterBarrier("links-disconnected")

      runOn(node1, node4, node5, node6, node7, node8) {
        waitForSurvivors(node1, node4, node5, node6, node7, node8)
        waitExistsAllDownOrGone(
          Seq(Seq(node2, node9), Seq(node2, node10), Seq(node3, node9), Seq(node3, node10))
        )
      }

      enterBarrier("split-brain-resolved")
    }
}
