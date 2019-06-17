package com.swissborg.sbr

import akka.cluster.Cluster
import akka.remote.testconductor.RoleName

import scala.concurrent.duration._

abstract class FiveNodeSpec(name: String, config: FiveNodeSpecConfig)
    extends SBRMultiNodeSpec(config) {
  def assertions(): Unit

  protected val node1: RoleName = config.node1
  protected val node2: RoleName = config.node2
  protected val node3: RoleName = config.node3
  protected val node4: RoleName = config.node4
  protected val node5: RoleName = config.node5

  override def initialParticipants: Int = roles.size

  // Starts the cluster in order so that the oldest
  // node can always be statically determined in the
  // tests.
  s"$name" must {
    "start node-1" in within(30 seconds) {
      runOn(node1) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1)
      }

      enterBarrier("node-1-up")
    }

    "start node-2" in within(30 seconds) {
      runOn(node2) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2)
      }

      enterBarrier("node-2-up")
    }

    "start node-3" in within(30 seconds) {
      runOn(node3) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3)
      }

      enterBarrier("node-3-up")
    }

    "start node-4" in within(30 seconds) {
      runOn(node4) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4)
      }

      enterBarrier("node-4-up")
    }

    "start node-5" in within(30 seconds) {
      runOn(node5) {
        Cluster(system).join(addressOf(node1))
        waitForUp(node1, node2, node3, node4, node5)
      }

      enterBarrier("node-5-up")
    }

    assertions()
  }
}
