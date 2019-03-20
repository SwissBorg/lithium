package akka.cluster.sbr.strategies.keepreferee.two

import akka.cluster.sbr.FiveNodeSpec
import akka.cluster.sbr.util.linksToKillForPartitions
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepRefereeSpec2MultiJvmNode1 extends KeepRefereeSpec2
class KeepRefereeSpec2MultiJvmNode2 extends KeepRefereeSpec2
class KeepRefereeSpec2MultiJvmNode3 extends KeepRefereeSpec2
class KeepRefereeSpec2MultiJvmNode4 extends KeepRefereeSpec2
class KeepRefereeSpec2MultiJvmNode5 extends KeepRefereeSpec2

class KeepRefereeSpec2 extends FiveNodeSpec("KeepReferee", KeepRefereeSpec2Config) {
  override def assertions(): Unit =
    "Three partitions, bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Partition of node1, node2
        // Partition of node 3
        // Partition of node 4
        // Partition of node 5
        linksToKillForPartitions(List(List(node1, node2), List(node3), List(node4), List(node5))).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node1, node2) {
        waitForUp(node1, node2)
        waitToBecomeUnreachable(node3, node4, node5)
      }

      enterBarrier("node-3-4-5-unreachable")

      runOn(node3) {
        waitForUp(node3)
        waitToBecomeUnreachable(node1, node2, node4, node5)
      }

      enterBarrier("node-1-2-4-5-unreachable")

      runOn(node4) {
        waitForUp(node4)
        waitToBecomeUnreachable(node1, node2, node3, node5)
      }

      enterBarrier("node-1-2-3-5-unreachable")

      runOn(node5) {
        waitForUp(node5)
        waitToBecomeUnreachable(node1, node2, node3, node4)
      }

      enterBarrier("node1-2-3-4-unreachable")

      runOn(node1, node2) {
        waitForUnreachableHandling()
        waitForSurvivors(node1, node2)
      }

      enterBarrier("node3-4-5-downed")
    }

}
