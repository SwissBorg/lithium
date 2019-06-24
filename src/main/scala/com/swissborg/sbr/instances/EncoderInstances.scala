package com.swissborg.sbr
package instances

import akka.actor.Address
import akka.cluster.{MemberStatus, UniqueAddress}
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

trait EncoderInstances {
  implicit val addressEncoder: Encoder[Address] = deriveEncoder[Address]

  implicit val uniqueAddressEncoder: Encoder[UniqueAddress] = deriveEncoder[UniqueAddress]

  implicit val memberStatusEncoder: Encoder[MemberStatus] = deriveEncoder[MemberStatus]
}
