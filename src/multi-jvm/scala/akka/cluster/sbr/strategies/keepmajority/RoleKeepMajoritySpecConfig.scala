package akka.cluster.sbr.strategies.keepmajority

import akka.cluster.sbr.FiveNodeConfig
import com.typesafe.config.ConfigFactory

object RoleKeepMajoritySpecConfig extends FiveNodeConfig("role_keep_majority_spec.conf") {
  nodeConfig(node1, node2, node3)(ConfigFactory.parseString("""akka.cluster.roles = ["foo"]"""))
}
