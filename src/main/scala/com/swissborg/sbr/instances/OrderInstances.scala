package com.swissborg.sbr
package instances

import akka.cluster.{Member, UniqueAddress}
import cats.Order

trait OrderInstances {
  implicit val memberOrder: Order[Member] = Order.fromOrdering(Member.ordering)

  implicit val uniqueAdressOrder: Order[UniqueAddress] = Order.from((a1, a2) => a1.compare(a2))
}
