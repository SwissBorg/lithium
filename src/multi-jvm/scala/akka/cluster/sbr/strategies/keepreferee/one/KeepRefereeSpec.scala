package akka.cluster.sbr.strategies.keepreferee.one

import akka.cluster.sbr.ThreeNodeSpec
import akka.cluster.sbr.strategies.keepreferee.KeepRefereeSpecThreeNodeConfig
import akka.remote.transport.ThrottlerTransportAdapter.Direction

import scala.concurrent.duration._

class KeepRefereeSpecMultiJvmNode1 extends KeepRefereeSpec
class KeepRefereeSpecMultiJvmNode2 extends KeepRefereeSpec
class KeepRefereeSpecMultiJvmNode3 extends KeepRefereeSpec

/**
 * Creates the partitions:
 *   (1) node1
 *   (2) node2, node3
 *
 * (1) should survive as it contains the referee.
 * (2) should down itself as it does not contain the referee.
 */
class KeepRefereeSpec extends ThreeNodeSpec("KeepReferee", KeepRefereeSpecThreeNodeConfig) {
  override def assertions(): Unit =
    "Bidirectional link failure" in within(60 seconds) {
      runOn(node1) {
        akka.cluster.sbr.util.linksToKillForPartitions(List(node1) :: List(node2, node3) :: Nil).foreach {
          case (from, to) => testConductor.blackhole(from, to, Direction.Both).await
        }
      }

      enterBarrier("node2-3-disconnected")

      runOn(node1) {
        waitForUp(node1)
        waitToBecomeUnreachable(node2, node3)
      }

      runOn(node2, node3) {
        waitForUp(node2, node3)
        waitToBecomeUnreachable(node1)
      }

      enterBarrier("node2-3-unreachable")

      runOn(node1) {
        waitForSurvivors(node1)
        waitForDownOrGone(node2, node3)
      }

      enterBarrier("node2-3-downed")

      runOn(node2, node3) {
        waitForSelfDowning
      }

      enterBarrier("node-2-3-suicide")
    }
}
