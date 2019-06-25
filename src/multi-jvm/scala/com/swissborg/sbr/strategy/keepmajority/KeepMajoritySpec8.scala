package com.swissborg.sbr
package strategy
package keepmajority

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepMajoritySpec8MultiJvmNode1 extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode2 extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode3 extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode4 extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode5 extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode6 extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode7 extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode8 extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode9 extends KeepMajority8
class KeepMajoritySpec8MultiJvmNode10 extends KeepMajority8

/**
  * Node9 and node10 are indirectly connected in a ten node cluster
  */
class KeepMajority8 extends TenNodeSpec("KeepMajority", KeepMajoritySpecTenNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 8" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node9, node10, Direction.Receive).await
        testConductor.blackhole(node2, node3, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node4, node5, node6, node7, node8) {
        waitForSurvivors(node1, node4, node5, node6, node7, node8)
        waitExistsAllDownOrGone(
          Seq(Seq(node2, node9), Seq(node2, node10), Seq(node3, node9), Seq(node3, node10))
        )
      }

      enterBarrier("split-brain-resolved")
    }
}
