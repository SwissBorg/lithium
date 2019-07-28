package akka.cluster.swissborg

import akka.actor.Address
import akka.cluster.ClusterSettings.DataCenter
import akka.cluster.{ClusterSettings, Member, MemberStatus, UniqueAddress}

object TestMember {

  def apply(address: Address, status: MemberStatus): Member =
    apply(address, status, Set.empty[String], ClusterSettings.DefaultDataCenter)

  def apply(address: Address, status: MemberStatus, dataCenter: DataCenter): Member =
    apply(address, status, Set.empty[String], dataCenter)

  def apply(
      address: Address,
      status: MemberStatus,
      roles: Set[String]
  ): Member = apply(address, status, roles, dataCenter = ClusterSettings.DefaultDataCenter)

  def apply(
      address: Address,
      status: MemberStatus,
      roles: Set[String],
      dataCenter: DataCenter
  ): Member =
    withUniqueAddress(UniqueAddress(address, 0L), status, roles, dataCenter)

  def withUniqueAddress(
      uniqueAddress: UniqueAddress,
      status: MemberStatus,
      roles: Set[String],
      dataCenter: DataCenter
  ): Member =
    new Member(
      uniqueAddress,
      Int.MaxValue,
      status,
      roles + (ClusterSettings.DcRolePrefix + dataCenter)
    )
}
