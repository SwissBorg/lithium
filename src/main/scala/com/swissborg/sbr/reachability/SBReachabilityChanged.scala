package com.swissborg.sbr.reachability

import akka.cluster.swissborg.SBReachability

final case class SBReachabilityChanged(reachability: SBReachability)
