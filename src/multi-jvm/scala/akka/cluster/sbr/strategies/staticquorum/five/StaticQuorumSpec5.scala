package akka.cluster.sbr.strategies.staticquorum.five

import akka.cluster.Cluster
import akka.cluster.sbr.FiveNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class StaticQuorumSpec5MultiJvmNode1 extends StaticQuorumSpec5
class StaticQuorumSpec5MultiJvmNode2 extends StaticQuorumSpec5
class StaticQuorumSpec5MultiJvmNode3 extends StaticQuorumSpec5
class StaticQuorumSpec5MultiJvmNode4 extends StaticQuorumSpec5
class StaticQuorumSpec5MultiJvmNode5 extends StaticQuorumSpec5

class StaticQuorumSpec5 extends FiveNodeSpec("StaticQuorum", StaticQuorumSpec5Config) {
  override def assertions(): Unit =
    "Unidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        // Node4 cannot receive node5 messages
        val _ = testConductor.blackhole(node4, node5, Direction.Receive).await
      }

      enterBarrier("node5-disconnected")

      runOn(node4) {
        waitForUp(node4)
        waitToBecomeUnreachable(node5)
      }

      enterBarrier("node5-unreachable")

      runOn(node5) {
        waitForUp(node5)
        waitToBecomeUnreachable(node4)
      }

      enterBarrier("node4-unreachable")

      runOn(node1, node2, node3) {
        waitForUp(node1, node2, node3)
        waitToBecomeUnreachable(node4, node5)
      }

      enterBarrier("node4-5-unreachable")

      runOn(node1, node2, node3) {
        waitForUnreachableHandling()
        waitForSurvivors(node1, node2, node3)
      }

      enterBarrier("node4-5-downed")

      runOn(node1, node2, node3, node4, node5) {
        println(s"AAA-${Cluster(system).state.members}")
      }
    }
}
