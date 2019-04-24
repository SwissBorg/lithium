package akka.cluster.sbr.strategies.staticquorum.eight

import akka.cluster.sbr.TenNodeSpec
import akka.cluster.sbr.strategies.staticquorum.StaticQuorumSpec3Config
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class StaticQuorumSpec8MultiJvmNode1  extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode2  extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode3  extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode4  extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode5  extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode6  extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode7  extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode8  extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode9  extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode10 extends StaticQuorumSpec8

/**
 * Node2 and node3 are indirectly connected in a ten node cluster
 * Node9 and node10 are indirectly connected in a ten node cluster
 */
class StaticQuorumSpec8 extends TenNodeSpec("StaticQuorum", StaticQuorumSpec3Config) {
  override def assertions(): Unit =
    "Split-brain" in within(120 seconds) {
      runOn(node1) {
        // Node9 cannot receive node10 messages
        val a = testConductor.blackhole(node9, node10, Direction.Receive).await
        val b = testConductor.blackhole(node2, node3, Direction.Receive).await
      }

      enterBarrier("links-disconnected")

      runOn(node1, node4, node5, node6, node7, node8) {
        waitForSurvivors(node1, node4, node5, node6, node7, node8)
        waitExistsAllDownOrGone(
          Seq(Seq(node2, node9), Seq(node2, node10), Seq(node3, node9), Seq(node3, node10))
        )
      }

      enterBarrier("split-brain-resolved")
    }
}
