package akka.cluster.sbr

import akka.cluster.Member
import cats.Order
import shapeless.tag.@@
import cats.implicits._
import cats.kernel.Eq

object implicits {
  implicit val memberOrder: Order[Member]              = Order.fromOrdering(Member.ordering)
  implicit def taggedOrder[A: Order, T]: Order[A @@ T] = Order[A].contramap(identity)

  implicit val memberEq: Eq[Member] = (x: Member, y: Member) => memberOrder.eqv(x, y)
}
