package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.FiveNodeSpec
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
        // Partition of node3, node4, and node 5.
        // Partition of node1 and node2.
        testConductor.blackhole(node3, node2, Direction.Both).await
        testConductor.blackhole(node1, node3, Direction.Both).await
        testConductor.blackhole(node2, node4, Direction.Both).await
        testConductor.blackhole(node2, node5, Direction.Both).await
        testConductor.blackhole(node1, node4, Direction.Both).await
        testConductor.blackhole(node1, node5, Direction.Both).await
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

      enterBarrier("node-3-4-5-unreachable")

      runOn(node1, node2) {
        waitForUnreachableHandling()
        waitForSurvivors(node1, node2)
      }

      enterBarrier("node-3-4-5-downed")
    }
}
