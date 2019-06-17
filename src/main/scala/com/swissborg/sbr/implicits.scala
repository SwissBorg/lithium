package com.swissborg.sbr

import akka.actor.Address
import akka.cluster.{Member, MemberStatus, UniqueAddress}
import cats.{Eq, Order}
import io.circe.Encoder
import io.circe.generic.semiauto._

object implicits extends implicits

trait implicits {
  implicit val memberOrder: Order[Member] = Order.fromOrdering(Member.ordering)

  implicit val uniqueAddressEq: Eq[UniqueAddress] = Eq.fromUniversalEquals

  implicit val uniqueAdressOrder: Order[UniqueAddress] = Order.from((a1, a2) => a1.compare(a2))

  implicit val addressEq: Eq[Address] = Eq.fromUniversalEquals

  implicit val memberStatusEq: Eq[MemberStatus] = Eq.fromUniversalEquals

  implicit val addressEncoder: Encoder[Address] = deriveEncoder[Address]

  implicit val uniqueAddressEncoder: Encoder[UniqueAddress] = deriveEncoder[UniqueAddress]

  implicit val memberStatus: Encoder[MemberStatus] = deriveEncoder[MemberStatus]
}
