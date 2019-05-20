package com.swissborg.sbr.strategies.keepoldest.one

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.ThreeNodeSpec
import com.swissborg.sbr.strategies.keepoldest.KeepOldestSpecThreeNodeConfig

import scala.concurrent.duration._

class KeepOldestSpecMultiJvmNode1 extends KeepOldestSpec
class KeepOldestSpecMultiJvmNode2 extends KeepOldestSpec
class KeepOldestSpecMultiJvmNode3 extends KeepOldestSpec

/**
  * Creates the partitions:
  *   (1) node1
  *   (2) node2, node3
  *
  * (1) should survive as it contains the oldest.
  * (2) should down itself as it does not contain the oldest.
  */
class KeepOldestSpec extends ThreeNodeSpec("KeepOldest", KeepOldestSpecThreeNodeConfig) {
  override def assertions(): Unit =
    "handle a network partition" in within(60 seconds) {
      runOn(node1) {
        com.swissborg.sbr.TestUtil.linksToKillForPartitions(List(node1) :: List(node2, node3) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("node1-disconnected")

      runOn(node1) {
        waitForUp(node1)
        waitToBecomeUnreachable(node2, node3)
      }

      enterBarrier("node2-3-unreachable")

      runOn(node1) {
        waitForSurvivors(node1)
        waitForDownOrGone(node2, node3)
      }

      enterBarrier("node2-3-downed")

      runOn(node2, node3) {
        waitForSelfDowning
      }

      enterBarrier("node2-3-suicide")
    }
}
