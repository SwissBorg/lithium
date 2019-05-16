package com.swissborg.sbr

import akka.cluster.swissborg.SBReachability

final case class SBReachabilityChanged(reachability: SBReachability)
