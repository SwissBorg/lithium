package com.swissborg.sbr.strategies.keepmajority.nine

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TenNodeSpec
import com.swissborg.sbr.strategies.keepmajority.KeepMajoritySpecTenNodeConfig

import scala.concurrent.duration._

class KeepMajoritySpec9MultiJvmNode1  extends KeepMajoritySpec9
class KeepMajoritySpec9MultiJvmNode2  extends KeepMajoritySpec9
class KeepMajoritySpec9MultiJvmNode3  extends KeepMajoritySpec9
class KeepMajoritySpec9MultiJvmNode4  extends KeepMajoritySpec9
class KeepMajoritySpec9MultiJvmNode5  extends KeepMajoritySpec9
class KeepMajoritySpec9MultiJvmNode6  extends KeepMajoritySpec9
class KeepMajoritySpec9MultiJvmNode7  extends KeepMajoritySpec9
class KeepMajoritySpec9MultiJvmNode8  extends KeepMajoritySpec9
class KeepMajoritySpec9MultiJvmNode9  extends KeepMajoritySpec9
class KeepMajoritySpec9MultiJvmNode10 extends KeepMajoritySpec9

/**
 * Node2 and node3 are indirectly connected in a ten node cluster
 * Node9 and node10 are indirectly connected in a ten node cluster
 */
class KeepMajoritySpec9 extends TenNodeSpec("KeepMajority", KeepMajoritySpecTenNodeConfig) {
  override def assertions(): Unit =
    "Unidirectional link failure" in within(120 seconds) {
      runOn(node1) {
        val a = testConductor.blackhole(node8, node9, Direction.Receive).await
        val b = testConductor.blackhole(node9, node10, Direction.Receive).await
      }

      enterBarrier("links-disconnected")

      runOn(node1, node2, node3, node4, node5, node6, node7) {
        waitForSurvivors(node1, node4, node5, node6, node7)
        waitExistsAllDownOrGone(
          Seq(Seq(node8, node10), Seq(node9))
        )
      }

      enterBarrier("split-brain-resolved")
    }
}
