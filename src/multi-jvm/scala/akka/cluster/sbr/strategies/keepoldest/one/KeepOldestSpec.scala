package akka.cluster.sbr.strategies.keepoldest.one

import akka.cluster.sbr.ThreeNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepOldestSpecMultiJvmNode1 extends KeepOldestSpec
class KeepOldestSpecMultiJvmNode2 extends KeepOldestSpec
class KeepOldestSpecMultiJvmNode3 extends KeepOldestSpec

class KeepOldestSpec extends ThreeNodeSpec("KeepOldest", KeepOldestSpecConfig) {
  override def assertions(): Unit =
    "Bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Kill link to node1
        testConductor.blackhole(node2, node1, Direction.Both).await
        testConductor.blackhole(node3, node1, Direction.Both).await
      }

      enterBarrier("node1-disconnected")

      runOn(node1) {
        waitForUp(node1)
        waitToBecomeUnreachable(node2, node3)
      }

      enterBarrier("node2-3-unreachable")

      runOn(node1) {
        waitForUnreachableHandling()
        waitForSurvivors(node1)
      }

      enterBarrier("node2-3-downed")
    }
}
