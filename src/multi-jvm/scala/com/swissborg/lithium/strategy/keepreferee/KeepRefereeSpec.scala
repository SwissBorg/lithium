package com.swissborg.lithium

package strategy

package keepreferee

import akka.remote.transport.ThrottlerTransportAdapter.Direction

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
sealed abstract class KeepRefereeSpec extends ThreeNodeSpec("KeepReferee", KeepRefereeSpecThreeNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 1" in within(60 seconds) {
      runOn(node1) {
        com.swissborg.lithium.TestUtil
          .linksToKillForPartitions(List(node1) :: List(node2, node3) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }
      }

      enterBarrier("links-failed")

      runOn(node1) {
        waitForSurvivors(node1)
        waitForAllLeaving(node2, node3)
      }

      runOn(node2, node3) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
