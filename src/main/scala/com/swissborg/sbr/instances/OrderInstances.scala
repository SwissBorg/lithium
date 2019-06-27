package com.swissborg.sbr
package instances

import akka.cluster.{Member, UniqueAddress}
import cats.Order
import shapeless.tag.@@

trait OrderInstances {
  implicit val memberOrder: Order[Member] = Order.fromOrdering(Member.ordering)

  implicit val uniqueAdressOrder: Order[UniqueAddress] = Order.from((a1, a2) => a1.compare(a2))

  implicit def taggedOrder[A: Order, B]: Order[A @@ B] = Order.by(identity[A])
}
