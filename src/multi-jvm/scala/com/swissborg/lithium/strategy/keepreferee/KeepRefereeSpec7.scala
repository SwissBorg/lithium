package com.swissborg.lithium

package strategy

package keepreferee

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepRefereeSpec7MultiJvmNode1  extends KeepRefereeSpec7
class KeepRefereeSpec7MultiJvmNode2  extends KeepRefereeSpec7
class KeepRefereeSpec7MultiJvmNode3  extends KeepRefereeSpec7
class KeepRefereeSpec7MultiJvmNode4  extends KeepRefereeSpec7
class KeepRefereeSpec7MultiJvmNode5  extends KeepRefereeSpec7
class KeepRefereeSpec7MultiJvmNode6  extends KeepRefereeSpec7
class KeepRefereeSpec7MultiJvmNode7  extends KeepRefereeSpec7
class KeepRefereeSpec7MultiJvmNode8  extends KeepRefereeSpec7
class KeepRefereeSpec7MultiJvmNode9  extends KeepRefereeSpec7
class KeepRefereeSpec7MultiJvmNode10 extends KeepRefereeSpec7

/**
  * Node9 and node10 are indirectly connected in a ten node cluster
  */
sealed abstract class KeepRefereeSpec7 extends TenNodeSpec("KeepReferee", KeepRefereeSpecTenNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 7" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node9, node10, Direction.Both).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3, node4, node5, node6, node7, node8) {
        waitForSurvivors(node1, node2, node3, node4, node5, node6, node7, node8)
        waitExistsAllDownOrGone(Seq(Seq(node9), Seq(node10)))
      }

      enterBarrier("split-brain-resolved")
    }
}
