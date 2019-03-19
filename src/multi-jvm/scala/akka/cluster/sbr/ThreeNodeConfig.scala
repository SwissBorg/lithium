package akka.cluster.sbr

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

abstract class ThreeNodeConfig(resource: String) extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")

  commonConfig(ConfigFactory.parseResources(resource))

  testTransport(on = true)
}
