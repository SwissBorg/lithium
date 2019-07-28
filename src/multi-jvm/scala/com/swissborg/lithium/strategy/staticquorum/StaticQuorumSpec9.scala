package com.swissborg.lithium

package strategy

package staticquorum

import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class StaticQuorumSpec9MultiJvmNode1  extends StaticQuorumSpec9
class StaticQuorumSpec9MultiJvmNode2  extends StaticQuorumSpec9
class StaticQuorumSpec9MultiJvmNode3  extends StaticQuorumSpec9
class StaticQuorumSpec9MultiJvmNode4  extends StaticQuorumSpec9
class StaticQuorumSpec9MultiJvmNode5  extends StaticQuorumSpec9
class StaticQuorumSpec9MultiJvmNode6  extends StaticQuorumSpec9
class StaticQuorumSpec9MultiJvmNode7  extends StaticQuorumSpec9
class StaticQuorumSpec9MultiJvmNode8  extends StaticQuorumSpec9
class StaticQuorumSpec9MultiJvmNode9  extends StaticQuorumSpec9
class StaticQuorumSpec9MultiJvmNode10 extends StaticQuorumSpec9

/**
  * Node2 and node3 are indirectly connected in a ten node cluster
  * Node9 and node10 are indirectly connected in a ten node cluster
  */
sealed abstract class StaticQuorumSpec9 extends TenNodeSpec("StaticQuorum", StaticQuorumSpec3Config) {
  override def assertions(): Unit =
    "handle scenario 9" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node8, node9, Direction.Receive).await
        testConductor.blackhole(node9, node10, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3, node4, node5, node6, node7) {
        waitForSurvivors(node1, node4, node5, node6, node7)
        waitExistsAllDownOrGone(
          Seq(Seq(node8, node10), Seq(node9))
        )
      }

      enterBarrier("split-brain-resolved")
    }
}
