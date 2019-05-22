package com.swissborg.sbr

import akka.cluster.{Member, MemberStatus, UniqueAddress}
import com.swissborg.sbr.implicits._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

final case class SimpleMember(uniqueAddress: UniqueAddress, status: MemberStatus)

object SimpleMember {
  def fromMember(member: Member): SimpleMember = SimpleMember(member.uniqueAddress, member.status)

  implicit val simpleMemberEncoder: Encoder[SimpleMember] = deriveEncoder
}
