package com.swissborg.sbr.strategy.keepmajority.four

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.ThreeNodeSpec
import com.swissborg.sbr.strategy.keepmajority.KeepMajoritySpecThreeNodeConfig

import scala.concurrent.duration._

class KeepMajoritySpec4MultiJvmNode1 extends KeepMajoritySpec4
class KeepMajoritySpec4MultiJvmNode2 extends KeepMajoritySpec4
class KeepMajoritySpec4MultiJvmNode3 extends KeepMajoritySpec4

class KeepMajoritySpec4 extends ThreeNodeSpec("KeepMajority", KeepMajoritySpecThreeNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 4" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node2, node3, Direction.Receive).await
      }

      enterBarrier("split-brain")

      runOn(node2, node3) {
        waitForSelfDowning
      }

      runOn(node1) {
        waitForAllLeaving(node2, node3)
      }

      enterBarrier("split-brain-resolved")
    }
}
