package com.swissborg.lithium

package instances

import akka.actor.Address
import akka.cluster.{MemberStatus, UniqueAddress}
import cats.Eq

trait EqInstances {
  implicit val uniqueAddressEq: Eq[UniqueAddress] = Eq.fromUniversalEquals

  implicit val addressEq: Eq[Address] = Eq.fromUniversalEquals

  implicit val memberStatusEq: Eq[MemberStatus] = Eq.fromUniversalEquals
}
