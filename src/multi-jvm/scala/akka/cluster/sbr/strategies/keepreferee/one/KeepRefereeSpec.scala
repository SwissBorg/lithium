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
        // Partition with node1           <- survive (contains referee)
        // Partiion with node2 and node3  <- killed
        akka.cluster.sbr.util.linksToKillForPartitions(List(node1) :: List(node2, node3) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("node2-3-disconnected")

      runOn(node1) {
        waitForUp(node1)
        waitToBecomeUnreachable(node2, node3)
      }

      runOn(node2, node3) {
        waitForUp(node2, node3)
        waitToBecomeUnreachable(node1)
      }

      enterBarrier("node2-3-unreachable")

      runOn(node1) {
        waitForSurvivors(node1)
        waitForDownOrGone(node2, node3)
      }

      enterBarrier("node2-3-downed")

      runOn(node2, node3) {
        waitForSelfDowning
      }

      enterBarrier("node-2-3-suicide")
    }
}
