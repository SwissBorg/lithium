package akka.cluster.sbr.strategies.keepreferee.three

import akka.cluster.sbr.ThreeNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepRefereeSpec3MultiJvmNode1 extends KeepRefereeSpec3
class KeepRefereeSpec3MultiJvmNode2 extends KeepRefereeSpec3
class KeepRefereeSpec3MultiJvmNode3 extends KeepRefereeSpec3

class KeepRefereeSpec3 extends ThreeNodeSpec("KeepReferee", KeepRefereeSpec3Config) {
  override def assertions(): Unit =
    "Unidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Node1 cannot receive node2 messages
        // The cluster will shut down as node1 is the referee but indirectly connected.
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
