package akka.cluster.swissborg

import akka.actor.Address
import akka.cluster.{ClusterSettings, Member, MemberStatus, UniqueAddress}

object TestMember {
  def apply(address: Address, status: MemberStatus): Member =
    apply(address, status, Set.empty[String])

  def apply(address: Address, status: MemberStatus, roles: Set[String]): Member =
    withUniqueAddress(UniqueAddress(address, 0L), status, roles)

  private def withUniqueAddress(
      uniqueAddress: UniqueAddress,
      status: MemberStatus,
      roles: Set[String]
  ): Member =
    new Member(
      uniqueAddress,
      Int.MaxValue,
      status,
      roles + (ClusterSettings.DcRolePrefix + ClusterSettings.DefaultDataCenter)
    )
}
