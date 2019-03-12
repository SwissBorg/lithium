package akka.cluster.sbr

import akka.cluster.Member
import cats.Order

object implicits {
  implicit val memberOrder: Order[Member] = Order.fromOrdering
}
