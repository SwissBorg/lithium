package strategies.staticquorum

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

object StaticQuorumSpecConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")

  commonConfig(ConfigFactory.parseResources("static_quorum_spec.conf"))

  testTransport(on = true)
}
