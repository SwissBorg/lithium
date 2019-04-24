package akka.cluster.sbr.strategies.keepreferee.eight

import akka.cluster.sbr.TenNodeSpec
import akka.cluster.sbr.strategies.keepreferee.KeepRefereeSpecTenNodeConfig
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepRefereeSpec8MultiJvmNode1  extends KeepRefereeSpec8
class KeepRefereeSpec8MultiJvmNode2  extends KeepRefereeSpec8
class KeepRefereeSpec8MultiJvmNode3  extends KeepRefereeSpec8
class KeepRefereeSpec8MultiJvmNode4  extends KeepRefereeSpec8
class KeepRefereeSpec8MultiJvmNode5  extends KeepRefereeSpec8
class KeepRefereeSpec8MultiJvmNode6  extends KeepRefereeSpec8
class KeepRefereeSpec8MultiJvmNode7  extends KeepRefereeSpec8
class KeepRefereeSpec8MultiJvmNode8  extends KeepRefereeSpec8
class KeepRefereeSpec8MultiJvmNode9  extends KeepRefereeSpec8
class KeepRefereeSpec8MultiJvmNode10 extends KeepRefereeSpec8

/**
 * Node3 and node4 are indirectly connected in a ten node cluster
 * Node9 and node10 are indirectly connected in a ten node cluster
 */
class KeepRefereeSpec8 extends TenNodeSpec("KeepReferee", KeepRefereeSpecTenNodeConfig) {
  override def assertions(): Unit =
    "Split-brain" in within(120 seconds) {
      runOn(node1) {
        // Node9 cannot receive node10 messages
        val a = testConductor.blackhole(node9, node10, Direction.Receive).await
        val b = testConductor.blackhole(node3, node4, Direction.Receive).await
      }

      enterBarrier("links-disconnected")

      runOn(node1, node2, node5, node6, node7, node8) {
        waitForSurvivors(node1, node2, node5, node6, node7, node8)
        waitExistsAllDownOrGone(
          Seq(Seq(node3, node9), Seq(node3, node10), Seq(node4, node9), Seq(node4, node10))
        )
      }

      enterBarrier("split-brain-resolved")
    }
}
