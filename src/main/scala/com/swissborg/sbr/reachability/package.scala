package com.swissborg.sbr

import akka.cluster.UniqueAddress

package object reachability {
  private[reachability] type Observer = UniqueAddress
  private[reachability] type Subject = UniqueAddress
  private[reachability] type Protester = UniqueAddress
  private[reachability] type Version = Long
}
