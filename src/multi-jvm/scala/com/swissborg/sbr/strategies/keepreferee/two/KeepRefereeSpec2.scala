package com.swissborg.sbr.strategies.keepreferee.two

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.FiveNodeSpec
import com.swissborg.sbr.TestUtil.linksToKillForPartitions
import com.swissborg.sbr.strategies.keepreferee.KeepRefereeSpecFiveNodeConfig

import scala.concurrent.duration._

class KeepRefereeSpec2MultiJvmNode1 extends KeepRefereeSpec2
class KeepRefereeSpec2MultiJvmNode2 extends KeepRefereeSpec2
class KeepRefereeSpec2MultiJvmNode3 extends KeepRefereeSpec2
class KeepRefereeSpec2MultiJvmNode4 extends KeepRefereeSpec2
class KeepRefereeSpec2MultiJvmNode5 extends KeepRefereeSpec2

/**
 * Creates the partitions:
 *   (1) node1, node2
 *   (2) node3
 *   (3) node4
 *   (4) node5
 *
 * (1) should survive as it contains the referee.
 * (2) should down itself as it does not contain the referee.
 * (3) should down itself as it does not contain the referee.
 * (4) should down itself as it does not contain the referee.
 */
class KeepRefereeSpec2 extends FiveNodeSpec("KeepReferee", KeepRefereeSpecFiveNodeConfig) {
  override def assertions(): Unit =
    "handle a clean network partition" in within(60 seconds) {
      runOn(node1) {
        linksToKillForPartitions(List(List(node1, node2), List(node3), List(node4), List(node5))).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node1, node2) {
        waitToBecomeUnreachable(node3, node4, node5)
      }

      runOn(node3) {
        waitToBecomeUnreachable(node1, node2, node4, node5)
      }

      runOn(node4) {
        waitToBecomeUnreachable(node1, node2, node3, node5)
      }

      runOn(node5) {
        waitToBecomeUnreachable(node1, node2, node3, node4)
      }

      enterBarrier("split-brain")

      runOn(node1, node2) {
        waitForSurvivors(node1, node2)
        waitForDownOrGone(node3, node4, node5)
      }

      runOn(node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }

}
