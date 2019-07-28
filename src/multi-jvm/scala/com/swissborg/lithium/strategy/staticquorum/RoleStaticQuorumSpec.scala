package com.swissborg.lithium

package strategy

package staticquorum

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class RoleStaticQuorumSpecMultiJvmNode1 extends RoleStaticQuorumSpec
class RoleStaticQuorumSpecMultiJvmNode2 extends RoleStaticQuorumSpec
class RoleStaticQuorumSpecMultiJvmNode3 extends RoleStaticQuorumSpec
class RoleStaticQuorumSpecMultiJvmNode4 extends RoleStaticQuorumSpec
class RoleStaticQuorumSpecMultiJvmNode5 extends RoleStaticQuorumSpec

/**
  * Creates the partitions:
  *   (1) node1, node2
  *   (2) node3, node4, node5
  *
  * (1) should survive as it is a quorum within the nodes with the given role.
  * (2) should down itself as it is not a quorum within the nodes with the given role.
  */
sealed abstract class RoleStaticQuorumSpec extends FiveNodeSpec("StaticQuorum", RoleStaticQuorumSpecConfig) {
  override def assertions(): Unit =
    "handle scenario 3" in within(60 seconds) {
      runOn(node1) {
        com.swissborg.lithium.TestUtil
          .linksToKillForPartitions(List(node1, node2) :: List(node3, node4, node5) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }
      }

      enterBarrier("links-failed")

      runOn(node1, node2) {
        waitForAllLeaving(node3, node4, node5)
      }

      runOn(node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
