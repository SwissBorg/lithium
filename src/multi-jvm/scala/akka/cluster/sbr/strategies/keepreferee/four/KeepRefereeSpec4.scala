package akka.cluster.sbr.strategies.keepreferee.four

import akka.cluster.sbr.FiveNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepRefereeSpec4MultiJvmNode1 extends KeepRefereeSpec4
class KeepRefereeSpec4MultiJvmNode2 extends KeepRefereeSpec4
class KeepRefereeSpec4MultiJvmNode3 extends KeepRefereeSpec4
class KeepRefereeSpec4MultiJvmNode4 extends KeepRefereeSpec4
class KeepRefereeSpec4MultiJvmNode5 extends KeepRefereeSpec4

/**
 * Node4 and node5 are indirectly connected in a five node cluster
 *
 * Node4 and node5 should down themselves as they are indirectly connected.
 * The three other nodes survive as they can reach the referee.
 */
class KeepRefereeSpec4 extends FiveNodeSpec("KeepReferee", KeepRefereeSpec4Config) {
  override def assertions(): Unit =
    "Unidirectional link failure" in within(120 seconds) {
      runOn(node1) {
        // Node4 cannot receive node5 messages
        // Node4 and node5 will shutdown because they are indirectly connected.
        val _ = testConductor.blackhole(node4, node5, Direction.Receive).await
      }

      enterBarrier("node5-disconnected")

      runOn(node4) {
        waitForUp(node4)
        waitToBecomeUnreachable(node5)
      }

      enterBarrier("node5-unreachable")

      runOn(node5) {
        waitForUp(node5)
        waitToBecomeUnreachable(node4)
      }

      enterBarrier("node4-unreachable")

      runOn(node1, node2, node3) {
        waitForUp(node1, node2, node3)
        waitToBecomeUnreachable(node4, node5)
      }

      enterBarrier("node4-5-unreachable")

      runOn(node1, node2, node3) {
        waitForSurvivors(node1, node2, node3)
        waitForDownOrGone(node4, node5)
      }

      enterBarrier("node1-2-3-ok")

      runOn(node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("node4-5-suicide")
    }
}
