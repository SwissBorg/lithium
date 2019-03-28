package akka.cluster.sbr.strategies.staticquorum.two

import akka.cluster.sbr.FiveNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class StaticQuorumSpec2MultiJvmNode1 extends StaticQuorumSpec2
class StaticQuorumSpec2MultiJvmNode2 extends StaticQuorumSpec2
class StaticQuorumSpec2MultiJvmNode3 extends StaticQuorumSpec2
class StaticQuorumSpec2MultiJvmNode4 extends StaticQuorumSpec2
class StaticQuorumSpec2MultiJvmNode5 extends StaticQuorumSpec2

class StaticQuorumSpec2 extends FiveNodeSpec("StaticQuorum", StaticQuorumSpec2Config) {
  override def assertions(): Unit =
    "Three partitions, bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Partition of node1, node2, node3 <- survives
        // Partition of node 4              <- killed
        // Partition of node 5              <- killed
        akka.cluster.sbr.util
          .linksToKillForPartitions(List(node1, node2, node3) :: List(node4) :: List(node5) :: Nil)
          .foreach {
            case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
          }
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3) {
        waitForUp(node1, node2, node3)
        waitToBecomeUnreachable(node4, node5)
      }

      enterBarrier("node-4-5-unreachable")

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

      runOn(node1, node2, node3) {
        waitForSurvivors(node1, node2, node3)
        waitForDownOrGone(node4, node5)
      }

      enterBarrier("node4-5-downed")

      runOn(node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("node4-5-self-downed")
    }
}
