package strategies.staticquorum

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

object RoleStaticQuorumSpecConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")
  val node4: RoleName = role("node4")
  val node5: RoleName = role("node5")

  commonConfig(ConfigFactory.parseResources("role_static_quorum_spec.conf"))

  nodeConfig(node1, node2, node3)(ConfigFactory.parseString("""akka.cluster.roles = ["foo"]"""))

  testTransport(on = true)
}
