package com.swissborg.lithium

package strategy

package multidc

import com.typesafe.config.ConfigFactory

object MultiDcSpecConfig extends TenNodeSpecConfig("staticquorum/static_quorum_spec_2.conf") {
  nodeConfig(node6, node7, node8, node9, node10)(
    ConfigFactory.parseString("""akka.cluster.multi-data-center.self-data-center = "other"""")
  )
}
