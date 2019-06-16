package com.swissborg.sbr

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

abstract class TenNodeSpecConfig(resource: String) extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")
  val node4: RoleName = role("node4")
  val node5: RoleName = role("node5")
  val node6: RoleName = role("node6")
  val node7: RoleName = role("node7")
  val node8: RoleName = role("node8")
  val node9: RoleName = role("node9")
  val node10: RoleName = role("node10")

  commonConfig(ConfigFactory.parseResources(resource))

  testTransport(on = true)
}
