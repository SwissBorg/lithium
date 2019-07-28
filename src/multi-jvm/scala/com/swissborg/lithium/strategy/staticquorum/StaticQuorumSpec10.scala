package com.swissborg.lithium

package strategy

package staticquorum

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.lithium.TestUtil.linksToKillForPartitions

import scala.concurrent.duration._

class StaticQuorumSpec10MultiJvmNode1  extends StaticQuorumSpec10
class StaticQuorumSpec10MultiJvmNode2  extends StaticQuorumSpec10
class StaticQuorumSpec10MultiJvmNode3  extends StaticQuorumSpec10
class StaticQuorumSpec10MultiJvmNode4  extends StaticQuorumSpec10
class StaticQuorumSpec10MultiJvmNode5  extends StaticQuorumSpec10
class StaticQuorumSpec10MultiJvmNode6  extends StaticQuorumSpec10
class StaticQuorumSpec10MultiJvmNode7  extends StaticQuorumSpec10
class StaticQuorumSpec10MultiJvmNode8  extends StaticQuorumSpec10
class StaticQuorumSpec10MultiJvmNode9  extends StaticQuorumSpec10
class StaticQuorumSpec10MultiJvmNode10 extends StaticQuorumSpec10

/**
  * Network partition between node1 -...- node8 and node9 - node10.
  * Indirect connections between node7 and node8.
  */
sealed abstract class StaticQuorumSpec10 extends TenNodeSpec("StaticQuorum", StaticQuorumSpec3Config) {
  override def assertions(): Unit =
    "handle scenario 10" in within(120 seconds) {
      runOn(node1) {
        linksToKillForPartitions(
          List(List(node1, node2, node3, node4, node5, node6, node7, node8), List(node9, node10))
        ).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }

        testConductor.blackhole(node7, node8, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3, node4, node5, node6) {
        waitForSurvivors(node1, node2, node3, node4, node5, node6)
        waitExistsAllDownOrGone(
          Seq(Seq(node9, node10, node7), Seq(node9, node10, node8))
        )
      }

      runOn(node9, node10) {
        waitForSelfDowning
      }

      enterBarrier("split-brain-resolved")
    }
}
