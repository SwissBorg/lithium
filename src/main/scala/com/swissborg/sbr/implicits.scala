package com.swissborg.sbr

import akka.actor.{ActorPath, Address}
import akka.cluster.{Member, MemberStatus, UniqueAddress}
import cats.{Eq, Order}
import cats.implicits._

object implicits extends implicits

trait implicits {
  implicit val memberOrder: Order[Member] = Order.fromOrdering(Member.ordering)

  implicit val uniqueAddressEq: Eq[UniqueAddress] = Eq.fromUniversalEquals

  implicit val addressEq: Eq[Address] = Eq.fromUniversalEquals

  implicit val memberStatusEq: Eq[MemberStatus] = Eq.fromUniversalEquals

  implicit val actorPathEq: Eq[ActorPath] = (x: ActorPath, y: ActorPath) => {
    x.address === y.address && x.elements == y.elements
  }
}
