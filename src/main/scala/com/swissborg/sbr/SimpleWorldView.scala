package com.swissborg.sbr

import akka.cluster.UniqueAddress
import com.swissborg.sbr.implicits._
import io.circe.Encoder
import io.circe.generic.semiauto._

final case class SimpleWorldView(
  selfUniqueAddress: UniqueAddress,
  reachableMembers: List[SimpleMember],
  indirectlyConnectedMembers: List[SimpleMember],
  unreachableMembers: List[SimpleMember],
)

object SimpleWorldView {
  def fromWorldView(worldView: WorldView): SimpleWorldView =
    SimpleWorldView(
      worldView.selfUniqueAddress,
      worldView.reachableNodes.toList.map(n => SimpleMember.fromMember(n.member)),
      worldView.indirectlyConnectedNodes.toList.map(n => SimpleMember.fromMember(n.member)),
      worldView.unreachableNodes.toList.map(n => SimpleMember.fromMember(n.member))
    )

  implicit val simpleWorldViewEncoder: Encoder[SimpleWorldView] = deriveEncoder
}
