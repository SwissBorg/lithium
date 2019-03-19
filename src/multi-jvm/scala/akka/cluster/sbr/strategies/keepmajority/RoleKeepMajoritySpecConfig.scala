package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.FiveNodeSpecConfig
import com.typesafe.config.ConfigFactory

object RoleKeepMajoritySpecConfig extends FiveNodeSpecConfig("role_keep_majority_spec.conf") {
  nodeConfig(node1, node2, node3)(ConfigFactory.parseString("""akka.cluster.roles = ["foo"]"""))
}
