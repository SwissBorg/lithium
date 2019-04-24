package akka.cluster.sbr.strategies.keepmajority.three

import akka.cluster.sbr.FiveNodeSpec
import akka.cluster.sbr.strategies.keepmajority.RoleKeepMajoritySpecConfig
import akka.cluster.sbr.util.linksToKillForPartitions
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class RoleKeepMajoritySpecMultiJvmNode1 extends RoleKeepMajoritySpec
class RoleKeepMajoritySpecMultiJvmNode2 extends RoleKeepMajoritySpec
class RoleKeepMajoritySpecMultiJvmNode3 extends RoleKeepMajoritySpec
class RoleKeepMajoritySpecMultiJvmNode4 extends RoleKeepMajoritySpec
class RoleKeepMajoritySpecMultiJvmNode5 extends RoleKeepMajoritySpec

class RoleKeepMajoritySpec extends FiveNodeSpec("KeepMajority", RoleKeepMajoritySpecConfig) {
  override def assertions(): Unit =
    "Bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Partition with node1 and node2          <- survive (majority given role)
        // Partition with node3, node4, and node 5 <- killed
        linksToKillForPartitions(List(List(node1, node2), List(node3, node4, node5))).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node3, node4, node5) {
        waitForUp(node3, node4, node5)
        waitToBecomeUnreachable(node1, node2)
      }

      enterBarrier("node1-2-unreachable")

      runOn(node1, node2) {
        waitForUp(node1, node2)
        waitToBecomeUnreachable(node3, node4, node5)
      }

      enterBarrier("node3-4-5-unreachable")

      runOn(node1, node2) {
        waitForSurvivors(node1, node2)
        waitForDownOrGone(node3, node4, node5)
      }

      enterBarrier("node3-4-5-downed")

      runOn(node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("node3-4-5-suicde")
    }
}
