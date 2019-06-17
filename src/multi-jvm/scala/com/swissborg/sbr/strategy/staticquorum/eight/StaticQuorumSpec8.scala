package com.swissborg.sbr.strategy.staticquorum.eight

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TenNodeSpec
import com.swissborg.sbr.strategy.staticquorum.StaticQuorumSpec3Config

import scala.concurrent.duration._

class StaticQuorumSpec8MultiJvmNode1 extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode2 extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode3 extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode4 extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode5 extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode6 extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode7 extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode8 extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode9 extends StaticQuorumSpec8
class StaticQuorumSpec8MultiJvmNode10 extends StaticQuorumSpec8

/**
  * Node2 and node3 are indirectly connected in a ten node cluster
  * Node9 and node10 are indirectly connected in a ten node cluster
  */
class StaticQuorumSpec8 extends TenNodeSpec("StaticQuorum", StaticQuorumSpec3Config) {
  override def assertions(): Unit =
    "handle scenario 8" in within(120 seconds) {
      runOn(node1) {
        testConductor.blackhole(node9, node10, Direction.Receive).await
        testConductor.blackhole(node2, node3, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node4, node5, node6, node7, node8) {
        waitForSurvivors(node1, node4, node5, node6, node7, node8)
        waitExistsAllDownOrGone(
          Seq(Seq(node2, node9), Seq(node2, node10), Seq(node3, node9), Seq(node3, node10))
        )
      }

      enterBarrier("split-brain-resolved")
    }
}
