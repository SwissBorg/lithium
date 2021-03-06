package com.swissborg.lithium.strategy.keepoldest

import com.swissborg.lithium.FiveNodeSpecConfig
import com.typesafe.config.ConfigFactory

object RoleKeepOldestSpecDownAloneConfig
    extends FiveNodeSpecConfig("keepoldest/role_keep_oldest_spec_down_alone.conf") {
  nodeConfig(node2, node3, node4, node5)(
    ConfigFactory.parseString("""akka.cluster.roles = ["foo"]""")
  )
}
