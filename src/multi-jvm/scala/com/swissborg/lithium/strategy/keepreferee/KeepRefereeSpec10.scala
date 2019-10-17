package com.swissborg.lithium

package strategy

package keepreferee

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.lithium.TestUtil.linksToKillForPartitions

import scala.concurrent.duration._

class KeepRefereeSpec10MultiJvmNode1  extends KeepRefereeSpec10
class KeepRefereeSpec10MultiJvmNode2  extends KeepRefereeSpec10
class KeepRefereeSpec10MultiJvmNode3  extends KeepRefereeSpec10
class KeepRefereeSpec10MultiJvmNode4  extends KeepRefereeSpec10
class KeepRefereeSpec10MultiJvmNode5  extends KeepRefereeSpec10
class KeepRefereeSpec10MultiJvmNode6  extends KeepRefereeSpec10
class KeepRefereeSpec10MultiJvmNode7  extends KeepRefereeSpec10
class KeepRefereeSpec10MultiJvmNode8  extends KeepRefereeSpec10
class KeepRefereeSpec10MultiJvmNode9  extends KeepRefereeSpec10
class KeepRefereeSpec10MultiJvmNode10 extends KeepRefereeSpec10

/**
 * Network partition between node1 -...- node8 and node9 - node10.
 * Indirect connections between node7 and node8.
 */
sealed abstract class KeepRefereeSpec10 extends TenNodeSpec("KeepReferee", KeepRefereeSpecTenNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 10" in within(120 seconds) {
      runOn(node1) {
        linksToKillForPartitions(
          List(List(node1, node2, node3, node4, node5, node6, node7, node8), List(node9, node10))
        ).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }

        testConductor.blackhole(node7, node8, Direction.Both).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3, node4, node5, node6) {
        waitForSurvivors(node1, node2, node3, node4, node5, node6)
        waitExistsAllDownOrGone(
          Seq(Seq(node9, node10, node7), Seq(node9, node10, node8))
        )
      }

      runOn(node9, node10) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
