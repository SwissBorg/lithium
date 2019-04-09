package akka.cluster.sbr.strategies.keepoldest.four

import akka.cluster.sbr.ThreeNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepOldestSpec4MultiJvmNode1 extends KeepOldestSpec4
class KeepOldestSpec4MultiJvmNode2 extends KeepOldestSpec4
class KeepOldestSpec4MultiJvmNode3 extends KeepOldestSpec4

/**
 * Node1 and node2 are indirectly connected in a three node cluster
 *
 * Node1 and node2 should down themselves as they are indirectly connected.
 * Node3 should down itself as its not the oldest.
 */
class KeepOldestSpec4 extends ThreeNodeSpec("KeepOldest", KeepOldestSpec4Config) {
  override def assertions(): Unit =
    "Unidirectional link failure" in within(120 seconds) {
      runOn(node1) {
        val _ = testConductor.blackhole(node1, node2, Direction.Receive).await
      }

      enterBarrier("node3-disconnected")

      runOn(node1, node2) {
        waitForUp(node1, node2)
      }

      enterBarrier("node3-unreachable")

      enterBarrier("node1-3-up")

      runOn(node1) {
        waitToBecomeUnreachable(node2)
      }

      enterBarrier("node2-unreachable")

      runOn(node2) {
        waitToBecomeUnreachable(node1)
      }

      enterBarrier("node1-unreachable")

      runOn(node3) {
        waitToBecomeUnreachable(node1, node2)
      }

      enterBarrier("node2-3-unreachable")

      runOn(node1, node2, node3) {
        waitForSelfDowning
      }

      enterBarrier("node1-2-3-suicide")
    }
}
