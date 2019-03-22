package akka.cluster.sbr.strategies.keepoldest.two

import akka.cluster.sbr.FiveNodeSpec
import akka.cluster.sbr.util.linksToKillForPartitions
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepOldestSpec2MultiJvmNode1 extends KeepOldestSpec2
class KeepOldestSpec2MultiJvmNode2 extends KeepOldestSpec2
class KeepOldestSpec2MultiJvmNode3 extends KeepOldestSpec2
class KeepOldestSpec2MultiJvmNode4 extends KeepOldestSpec2
class KeepOldestSpec2MultiJvmNode5 extends KeepOldestSpec2

class KeepOldestSpec2 extends FiveNodeSpec("KeepOldest", KeepOldestSpec2Config) {
  override def assertions(): Unit =
    "Three partitions, bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Partition containing node1
        // Partition containing node2 and node3
        // Partition containing node4 and node 5
        linksToKillForPartitions(List(node1) :: List(node2, node3) :: List(node4, node5) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("link-failed")

      runOn(node1) {
        waitForUp(node1)
        waitToBecomeUnreachable(node2, node3, node4, node5)
      }

      enterBarrier("node2-3-4-5-unreachable")

      runOn(node2, node3) {
        waitForUp(node2, node3)
        waitToBecomeUnreachable(node1, node4, node5)
      }

      enterBarrier("node1-4-5-unreachable")

      runOn(node4, node5) {
        waitForUp(node4, node5)
        waitToBecomeUnreachable(node1, node2, node3)
      }

      enterBarrier("node1-2-3-unreachable")

      runOn(node1) {
        waitForUnreachableHandling()
        waitForSurvivors(node1)
      }

      enterBarrier("node2-3-4-5-downed")
    }
}