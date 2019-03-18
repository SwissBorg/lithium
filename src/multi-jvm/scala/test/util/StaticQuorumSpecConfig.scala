package test.util

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

object StaticQuorumSpecConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")

  commonConfig(
    ConfigFactory
      .parseString {
        """
          |akka {
          |  loglevel = DEBUG
          |  actor.provider = cluster
          |
          |  log-dead-letters = off
          |
          |  coordinated-shutdown.run-by-jvm-shutdown-hook = off
          |  coordinated-shutdown.terminate-actor-system = off
          |
          |  cluster {
          |    auto-join = off
          |    run-coordinated-shutdown-when-down = off
          |    #seed-nodes = []
          |
          |    downing-provider-class = "akka.cluster.sbr.DowningProviderImpl"
          |    split-brain-resolver {
          |      active-strategy = "static-quorum"
          |      static-quorum {
          |        quorum-size = 2
          |        role = ""
          |      }
          |    }
          |  }
          |}
      """.stripMargin
      }
  )

  testTransport(on = true)
}
