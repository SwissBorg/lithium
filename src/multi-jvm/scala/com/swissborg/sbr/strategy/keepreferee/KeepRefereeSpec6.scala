package com.swissborg.sbr
package strategy
package keepreferee

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepRefereeSpec6MultiJvmNode1 extends KeepRefereeSpec6

class KeepRefereeSpec6MultiJvmNode2 extends KeepRefereeSpec6

class KeepRefereeSpec6MultiJvmNode3 extends KeepRefereeSpec6

class KeepRefereeSpec6MultiJvmNode4 extends KeepRefereeSpec6

class KeepRefereeSpec6MultiJvmNode5 extends KeepRefereeSpec6

/**
  * The link between node1 and node4 fails.
  * The link between node3 and node5 fails.
  *
  * All nodes but node2 downs itself because they are indirectly connected.
  * Node2 survives as it is the referee.
  */
sealed abstract class KeepRefereeSpec6
    extends FiveNodeSpec("KeepReferee", KeepRefereeSpecFiveNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 6" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node1, node4, Direction.Receive).await
        testConductor.blackhole(node3, node5, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
