package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.Reachability.ReachabilityStatus
import akka.cluster.{Member, MemberStatus, UniqueAddress}
import cats.{Eq, Order}

object implicits {
  implicit val memberOrder: Order[Member] = Order.fromOrdering(Member.ordering)

  implicit val uniqueAddressEq: Eq[UniqueAddress]           = Eq.fromUniversalEquals
  implicit val addressEq: Eq[Address]                       = Eq.fromUniversalEquals
  implicit val reachabilityStatusEq: Eq[ReachabilityStatus] = Eq.fromUniversalEquals
  implicit val memberStatusEq: Eq[MemberStatus]             = Eq.fromUniversalEquals
}
