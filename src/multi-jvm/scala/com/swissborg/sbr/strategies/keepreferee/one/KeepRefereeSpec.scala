package com.swissborg.sbr.strategies.keepreferee.one

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.ThreeNodeSpec
import com.swissborg.sbr.strategies.keepreferee.KeepRefereeSpecThreeNodeConfig

import scala.concurrent.duration._

class KeepRefereeSpecMultiJvmNode1 extends KeepRefereeSpec
class KeepRefereeSpecMultiJvmNode2 extends KeepRefereeSpec
class KeepRefereeSpecMultiJvmNode3 extends KeepRefereeSpec

/**
 * Creates the partitions:
 *   (1) node1
 *   (2) node2, node3
 *
 * (1) should survive as it contains the referee.
 * (2) should down itself as it does not contain the referee.
 */
class KeepRefereeSpec extends ThreeNodeSpec("KeepReferee", KeepRefereeSpecThreeNodeConfig) {
  override def assertions(): Unit =
    "handle a clean network partition" in within(60 seconds) {
      runOn(node1) {
        com.swissborg.sbr.TestUtil.linksToKillForPartitions(List(node1) :: List(node2, node3) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node1) {
        waitToBecomeUnreachable(node2, node3)
      }

      runOn(node2, node3) {
        waitToBecomeUnreachable(node1)
      }

      enterBarrier("split-brain")

      runOn(node1) {
        waitForSurvivors(node1)
        waitForDownOrGone(node2, node3)
      }

      runOn(node2, node3) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
