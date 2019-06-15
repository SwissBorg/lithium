package com.swissborg.sbr.strategy.keepreferee

import com.swissborg.sbr.FiveNodeSpecConfig
import com.typesafe.config.ConfigFactory

object KeepRefereeSpecFiveNodeLessNodesConfig
    extends FiveNodeSpecConfig("keepreferee/keep_referee_spec_2_less_nodes.conf") {
  nodeConfig(node1)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9991"))
  nodeConfig(node2)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9992"))
  nodeConfig(node3)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9993"))
  nodeConfig(node4)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9994"))
  nodeConfig(node5)(ConfigFactory.parseString("akka.remote.netty.tcp.port=9995"))
}
