package com.swissborg.sbr.strategy.staticquorum.seven

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TenNodeSpec
import com.swissborg.sbr.strategy.staticquorum.StaticQuorumSpec3Config

import scala.concurrent.duration._

class StaticQuorumSpec7MultiJvmNode1  extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode2  extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode3  extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode4  extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode5  extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode6  extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode7  extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode8  extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode9  extends StaticQuorumSpec7
class StaticQuorumSpec7MultiJvmNode10 extends StaticQuorumSpec7

/**
 * Node9 and node10 are indirectly connected in a ten node cluster
 */
class StaticQuorumSpec7 extends TenNodeSpec("StaticQuorum", StaticQuorumSpec3Config) {
  override def assertions(): Unit =
    "handle scenario 7" in within(120 seconds) {
      runOn(node1) {
        // Node9 cannot receive node10 messages
        testConductor.blackhole(node9, node10, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3, node4, node5, node6, node7, node8) {
        waitForSurvivors(node1, node2, node3, node4, node5, node6, node7, node8)
        waitExistsAllDownOrGone(Seq(Seq(node9), Seq(node10)))
      }

      enterBarrier("split-brain-resolved")
    }
}
