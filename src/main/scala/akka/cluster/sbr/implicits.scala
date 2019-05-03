package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.Member
import cats.Order
import cats.implicits._
import cats.kernel.Eq
import shapeless.tag.@@

object implicits {
  implicit val memberOrder: Order[Member]              = Order.fromOrdering(Member.ordering)
  implicit def taggedOrder[A: Order, T]: Order[A @@ T] = Order[A].contramap(identity)

  implicit val addressOrder: Order[Address] = Order.fromOrdering(Address.addressOrdering)

  implicit val memberEq: Eq[Member] = (x: Member, y: Member) => memberOrder.eqv(x, y)
}
