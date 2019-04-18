package akka.cluster.sbr.strategies.staticquorum.seven

import akka.cluster.sbr.TenNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class StaticQuorumSpec7MultiJvmNode1 extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode2 extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode3 extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode4 extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode5 extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode6 extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode7 extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode8 extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode9 extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode10 extends StaticQuorumSpec7

/**
  * Node4 and node5 are indirectly connected in a five node cluster
  *
  * Node4 and node5 should down themselves as they are indirectly connected.
  * The three other nodes survive as they form a quorum.
  */
class StaticQuorumSpec7 extends TenNodeSpec("StaticQuorum", StaticQuorumSpec7Config) {
  override def assertions(): Unit =
    "Unidirectional link failure" in within(120 seconds) {
      runOn(node1) {
        // Node4 cannot receive node5 messages
        val _ = testConductor.blackhole(node9, node10, Direction.Receive).await
      }

      enterBarrier("node9-10-link-disconnected")

      runOn(node9) {
        waitForUp(node9)
        waitToBecomeUnreachable(node10)
      }

      enterBarrier("node10-unreachable")

      runOn(node10) {
        waitForUp(node10)
        waitToBecomeUnreachable(node9)
      }

      enterBarrier("node9-unreachable")

      runOn(node1, node2, node3, node4, node5, node6, node7, node8) {
        waitForUp(node1, node2, node3, node4, node5, node6, node7, node8)
        waitToBecomeUnreachable(node9, node10)
      }

      enterBarrier("node9-10-unreachable")

      runOn(node1, node2, node3, node4, node5, node6, node7, node8) {
        waitForSurvivors(node1, node2, node3, node4, node5, node6, node7, node8)
        waitForDownOrGone(node9, node10)
      }

      enterBarrier("node1-2-3-4-5-6-7-8-ok")

      runOn(node9, node10) {
        waitForSelfDowning
      }

      enterBarrier("node9-10-suicide")
    }
}
