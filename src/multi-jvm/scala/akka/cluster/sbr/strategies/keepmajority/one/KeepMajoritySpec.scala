package akka.cluster.sbr.strategies.keepmajority.one

import akka.cluster.sbr.ThreeNodeSpec
import akka.cluster.sbr.util.linksToKillForPartitions
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepMajoritySpecMultiJvmNode1 extends KeepMajoritySpec
class KeepMajoritySpecMultiJvmNode2 extends KeepMajoritySpec
class KeepMajoritySpecMultiJvmNode3 extends KeepMajoritySpec

class KeepMajoritySpec extends ThreeNodeSpec("KeepMajority", KeepMajoritySpecConfig) {
  override def assertions(): Unit =
    "Bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Partition with node1 and node2 <- survive
        // Partition with node3           <- killed
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
        waitForSurvivors(node1, node2)
        waitForDownOrGone(node3)
      }

      enterBarrier("node3-downed")

      runOn(node3) {
        waitForSelfDowning
      }

      enterBarrier("node3-suicide")
    }
}
