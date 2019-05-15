package com.swissborg.sbr.strategies.staticquorum

import com.swissborg.sbr.FiveNodeSpecConfig
import com.typesafe.config.ConfigFactory

object RoleStaticQuorumSpecConfig extends FiveNodeSpecConfig("staticquorum/role_static_quorum_spec.conf") {
  nodeConfig(node1, node2, node3)(ConfigFactory.parseString("""akka.cluster.roles = ["foo"]"""))
}
