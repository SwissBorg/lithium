package com.swissborg.lithium

import akka.cluster.UniqueAddress

package object reachability {
  type Observer = UniqueAddress
  type Subject  = UniqueAddress
}
