package akka.cluster.sbr.strategies.staticquorum.three

import akka.cluster.sbr.FiveNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class RoleStaticQuorumSpecMultiJvmNode1 extends RoleStaticQuorumSpec
class RoleStaticQuorumSpecMultiJvmNode2 extends RoleStaticQuorumSpec
class RoleStaticQuorumSpecMultiJvmNode3 extends RoleStaticQuorumSpec
class RoleStaticQuorumSpecMultiJvmNode4 extends RoleStaticQuorumSpec
class RoleStaticQuorumSpecMultiJvmNode5 extends RoleStaticQuorumSpec

class RoleStaticQuorumSpec extends FiveNodeSpec("StaticQuorum", RoleStaticQuorumSpecConfig) {
  override def assertions(): Unit =
    "Two partitions, bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Partition with node1 and node2          <- survive (majority given the role)
        // Partition with node3, node4, and node 5 <- killed
        akka.cluster.sbr.util.linksToKillForPartitions(List(node1, node2) :: List(node3, node4, node5) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("links-failed")

      runOn(node3, node4, node5) {
        waitForUp(node3, node4, node5)
        waitToBecomeUnreachable(node1, node2)
      }

      enterBarrier("node1-2-unreachable")

      runOn(node1, node2) {
        waitForUp(node1, node2)
        waitToBecomeUnreachable(node3, node4, node5)
      }

      enterBarrier("node-3-4-5-unreachable")

      runOn(node1, node2) {
        waitForSurvivors(node1, node2)
        waitForDownOrGone(node3, node4, node5)
      }

      enterBarrier("node-3-4-5-downed")

      runOn(node3, node4, node5) {
        waitForSelfDowning
      }

      enterBarrier("node3-4-5-suicide")
    }
}
