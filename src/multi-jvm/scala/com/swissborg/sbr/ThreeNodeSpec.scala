package com.swissborg.sbr

import akka.cluster.Cluster

import scala.concurrent.duration._

abstract class ThreeNodeSpec(name: String, config: ThreeNodeSpecConfig)
    extends SBRMultiNodeSpec(config) {

  def assertions(): Unit

  final protected val node1 = config.node1
  final protected val node2 = config.node2
  final protected val node3 = config.node3

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

    assertions()
  }
}
