package akka.cluster.sbr.strategies.staticquorum.four

import akka.cluster.sbr.ThreeNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class StaticQuorumSpec4MultiJvmNode1 extends StaticQuorumSpec4
class StaticQuorumSpec4MultiJvmNode2 extends StaticQuorumSpec4
class StaticQuorumSpec4MultiJvmNode3 extends StaticQuorumSpec4

/**
 * Node2 and node3 are indirectly connected in a three node cluster.
 *
 * Node2 and node3 should down themselves as they are indirectly connected.
 * Node1 should down itself since its not a quorum.
 */
class StaticQuorumSpec4 extends ThreeNodeSpec("StaticQuorum", StaticQuorumSpec4Config) {
  override def assertions(): Unit =
    "Unidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Node2 cannot receive node3 messages
        val _ = testConductor.blackhole(node2, node3, Direction.Receive).await
      }

      enterBarrier("node3-disconnected")

      runOn(node1, node2) {
        waitForUp(node1, node2)
      }

      enterBarrier("node3-unreachable")

      enterBarrier("node1-3-up")

      runOn(node2) {
        waitForUp(node2)
        waitToBecomeUnreachable(node3)
      }

      runOn(node1) {
        waitToBecomeUnreachable(node2, node3)
      }

      enterBarrier("node3-unreachable")

      runOn(node1, node2, node3) {
        waitForSelfDowning
      }

      enterBarrier("node1-2-3-suicide")
    }
}
