package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.FiveNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

class KeepMajoritySpec2MultiJvmNode1 extends KeepMajoritySpec2
class KeepMajoritySpec2MultiJvmNode2 extends KeepMajoritySpec2
class KeepMajoritySpec2MultiJvmNode3 extends KeepMajoritySpec2
class KeepMajoritySpec2MultiJvmNode4 extends KeepMajoritySpec2
class KeepMajoritySpec2MultiJvmNode5 extends KeepMajoritySpec2

class KeepMajoritySpec2 extends FiveNodeSpec("KeepMajority", KeepMajoritySpec2Config) {
  override def assertions(): Unit =
    "Three partitions, bidirectional link failure" in {
      runOn(node1) {
        // Partition of node1, node2, node3
        // Partition of node 4
        // Partition of node 5
        testConductor.blackhole(node1, node4, Direction.Both).await
        testConductor.blackhole(node2, node4, Direction.Both).await
        testConductor.blackhole(node3, node4, Direction.Both).await

        testConductor.blackhole(node1, node5, Direction.Both).await
        testConductor.blackhole(node2, node5, Direction.Both).await
        testConductor.blackhole(node3, node5, Direction.Both).await

        testConductor.blackhole(node4, node5, Direction.Both).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3) {
        waitForUp(node1, node2, node3)
        waitToBecomeUnreachable(node4, node5)
      }

      enterBarrier("node-4-5-unreachable")

      runOn(node4) {
        waitForUp(node4)
        waitToBecomeUnreachable(node1, node2, node3, node5)
      }

      enterBarrier("node-1-2-3-5-unreachable")

      runOn(node5) {
        waitForUp(node5)
        waitToBecomeUnreachable(node1, node2, node3, node4)
      }

      enterBarrier("node1-2-3-4-unreachable")

      runOn(node1, node2, node3) {
        waitForUnreachableHandling()
        waitForSurvivors(node1, node2, node3)
      }

      enterBarrier("node4-5-downed")
    }
}
