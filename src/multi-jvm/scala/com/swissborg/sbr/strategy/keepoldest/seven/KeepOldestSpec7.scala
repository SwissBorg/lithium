package com.swissborg.sbr.strategy.keepoldest.seven

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TenNodeSpec
import com.swissborg.sbr.strategy.keepoldest.KeepOldestSpecTenNodeConfig

import scala.concurrent.duration._

class KeepOldestSpec7MultiJvmNode1 extends KeepOldestSpec7
class KeepOldestSpec7MultiJvmNode2 extends KeepOldestSpec7
class KeepOldestSpec7MultiJvmNode3 extends KeepOldestSpec7
class KeepOldestSpec7MultiJvmNode4 extends KeepOldestSpec7
class KeepOldestSpec7MultiJvmNode5 extends KeepOldestSpec7
class KeepOldestSpec7MultiJvmNode6 extends KeepOldestSpec7
class KeepOldestSpec7MultiJvmNode7 extends KeepOldestSpec7
class KeepOldestSpec7MultiJvmNode8 extends KeepOldestSpec7
class KeepOldestSpec7MultiJvmNode9 extends KeepOldestSpec7
class KeepOldestSpec7MultiJvmNode10 extends KeepOldestSpec7

/**
  * Node9 and node10 are indirectly connected in a ten node cluster
  */
class KeepOldestSpec7 extends TenNodeSpec("KeepOldest", KeepOldestSpecTenNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 7" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node9, node10, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3, node4, node5, node6, node7, node8) {
        waitForSurvivors(node1, node2, node3, node4, node5, node6, node7, node8)
        waitExistsAllDownOrGone(Seq(Seq(node9), Seq(node10)))
      }

      enterBarrier("split-brain-resolved")
    }
}
