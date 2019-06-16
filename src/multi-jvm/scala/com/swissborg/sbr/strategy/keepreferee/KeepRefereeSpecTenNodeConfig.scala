package com.swissborg.sbr.strategy.keepreferee

import com.swissborg.sbr.TenNodeSpecConfig
import com.typesafe.config.ConfigFactory

object KeepRefereeSpecTenNodeConfig
    extends TenNodeSpecConfig("keepreferee/keep_referee_spec_3.conf") {
  nodeConfig(node1)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9991"))
  nodeConfig(node2)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9992"))
  nodeConfig(node3)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9993"))
  nodeConfig(node4)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9994"))
  nodeConfig(node5)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9995"))
  nodeConfig(node6)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9996"))
  nodeConfig(node7)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9997"))
  nodeConfig(node8)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9998"))
  nodeConfig(node9)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9999"))
  nodeConfig(node10)(ConfigFactory.parseString("akka.remote.netty.tcp.port=10000"))
}
