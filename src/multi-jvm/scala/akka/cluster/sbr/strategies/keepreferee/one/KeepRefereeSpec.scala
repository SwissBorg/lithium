package akka.cluster.sbr.strategies.keepreferee.one

import akka.cluster.sbr.ThreeNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepRefereeSpecMultiJvmNode1 extends KeepRefereeSpec
class KeepRefereeSpecMultiJvmNode2 extends KeepRefereeSpec
class KeepRefereeSpecMultiJvmNode3 extends KeepRefereeSpec

class KeepRefereeSpec extends ThreeNodeSpec("KeepReferee", KeepRefereeSpecConfig) {
  override def assertions(): Unit =
    "Bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Kill link bi-directionally to node3
        testConductor.blackhole(node2, node1, Direction.Both).await
        testConductor.blackhole(node3, node1, Direction.Both).await
      }

      enterBarrier("node2-3-disconnected")

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