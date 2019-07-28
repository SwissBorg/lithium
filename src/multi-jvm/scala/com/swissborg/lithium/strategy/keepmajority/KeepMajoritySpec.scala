package com.swissborg.lithium

package strategy

package keepmajority

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.lithium.TestUtil.linksToKillForPartitions

import scala.concurrent.duration._

class KeepMajoritySpecMultiJvmNode1 extends KeepMajoritySpec
class KeepMajoritySpecMultiJvmNode2 extends KeepMajoritySpec
class KeepMajoritySpecMultiJvmNode3 extends KeepMajoritySpec

sealed abstract class KeepMajoritySpec extends ThreeNodeSpec("KeepMajority", KeepMajoritySpecThreeNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 1" in within(60 seconds) {
      runOn(node1) {
        linksToKillForPartitions(List(node1, node2) :: List(node3) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node1, node2) {
        waitForSurvivors(node1, node2)
        waitForAllLeaving(node3)
      }

      runOn(node3) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
