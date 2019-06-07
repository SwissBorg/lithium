package akka.cluster.sbr.strategies.keepmajority.seven

import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.swissborg.sbr.TenNodeSpec
import com.swissborg.sbr.strategy.keepmajority.KeepMajoritySpecTenNodeConfig

import scala.concurrent.duration._

class KeepMajoritySpec7MultiJvmNode1  extends KeepMajority7
class KeepMajoritySpec7MultiJvmNode2  extends KeepMajority7
class KeepMajoritySpec7MultiJvmNode3  extends KeepMajority7
class KeepMajoritySpec7MultiJvmNode4  extends KeepMajority7
class KeepMajoritySpec7MultiJvmNode5  extends KeepMajority7
class KeepMajoritySpec7MultiJvmNode6  extends KeepMajority7
class KeepMajoritySpec7MultiJvmNode7  extends KeepMajority7
class KeepMajoritySpec7MultiJvmNode8  extends KeepMajority7
class KeepMajoritySpec7MultiJvmNode9  extends KeepMajority7
class KeepMajoritySpec7MultiJvmNode10 extends KeepMajority7

/**
 * Node9 and node10 are indirectly connected in a ten node cluster
 */
class KeepMajority7 extends TenNodeSpec("KeepMajority", KeepMajoritySpecTenNodeConfig) {
  override def assertions(): Unit =
    "handle scenario 7" in within(120 seconds) {
      runOn(node1) {
        // Node9 cannot receive node10 messages
        val _ = testConductor.blackhole(node9, node10, Direction.Receive).await
      }

      enterBarrier("links-failed")

      runOn(node1, node2, node3, node4, node5, node6, node7, node8) {
        waitForSurvivors(node1, node2, node3, node4, node5, node6, node7, node8)
        waitExistsAllDownOrGone(Seq(Seq(node9), Seq(node10)))
      }

      enterBarrier("split-brain-resolved")
    }
}
