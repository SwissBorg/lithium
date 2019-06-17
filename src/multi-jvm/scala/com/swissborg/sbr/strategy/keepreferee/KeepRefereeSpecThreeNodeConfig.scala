package com.swissborg.sbr.strategy.keepreferee

import com.swissborg.sbr.ThreeNodeSpecConfig
import com.typesafe.config.ConfigFactory

object KeepRefereeSpecThreeNodeConfig
    extends ThreeNodeSpecConfig("keepreferee/keep_referee_spec.conf") {
  nodeConfig(node1)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9991"))
  nodeConfig(node2)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9992"))
  nodeConfig(node3)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9993"))
}
