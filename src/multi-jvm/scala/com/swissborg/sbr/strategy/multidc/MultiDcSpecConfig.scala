package com.swissborg.sbr
package strategy
package multidc

import com.typesafe.config.ConfigFactory

object MultiDcSpecConfig extends FiveNodeSpecConfig("staticquorum/static_quorum_spec.conf") {
  nodeConfig(node4, node5)(
    ConfigFactory.parseString("""akka.cluster.multi-data-center.self-data-center = "other""""))
}
