package com.swissborg.sbr

import akka.cluster.UniqueAddress

package object reachability {
  type Observer = UniqueAddress
  type Subject = UniqueAddress
  type Protester = UniqueAddress
  type Version = Long
}
