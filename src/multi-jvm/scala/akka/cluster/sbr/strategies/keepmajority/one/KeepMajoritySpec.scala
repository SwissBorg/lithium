package akka.cluster.sbr.strategies.keepmajority.one

import akka.cluster.sbr.ThreeNodeSpec
import akka.cluster.sbr.util.linksToKillForPartitions
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepMajoritySpecMultiJvmNode1 extends KeepMajoritySpec
class KeepMajoritySpecMultiJvmNode2 extends KeepMajoritySpec
class KeepMajoritySpecMultiJvmNode3 extends KeepMajoritySpec

class KeepMajoritySpec extends ThreeNodeSpec("KeepMajority", KeepMajoritySpecConfig) {
  override def assertions(): Unit = {
    "Bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Kill link bi-directionally to node3
        linksToKillForPartitions(List(node1, node2) :: List(node3) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("node3-disconnected")

      runOn(node1, node2) {
        waitForUp(node1, node2)
        waitToBecomeUnreachable(node3)
      }

      enterBarrier("node3-unreachable")

      runOn(node1, node2) {
        waitForUnreachableHandling()
        waitForSurvivors(node1, node2)
      }

      enterBarrier("node3-downed")
    }

    "Complete bidirectional link failure" in within(30 seconds) {
      runOn(node1) {
        val _ = testConductor.blackhole(node1, node2, Direction.Both).await
      }

      enterBarrier("all-disconnected")

      runOn(node1) {
        waitToBecomeUnreachable(node2)
        // TODO How to check if all killed?
      }

      enterBarrier("all-downed")
    }
  }
}