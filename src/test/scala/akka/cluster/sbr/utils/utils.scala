package akka.cluster.sbr

import akka.actor.Address
import akka.cluster.{ClusterSettings, Member, MemberStatus, UniqueAddress}
import cats.data.{NonEmptyList, NonEmptySet}
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._

package object utils {

  /**
   * Splits `as` in `parts` parts of arbitrary sizes.
   * If `parts` is less than or more than the size of `as` it will return `NonEmptySet(as)`.
   */
  def splitIn[A](parts: Int Refined Positive, as: NonEmptySet[A]): Arbitrary[NonEmptyList[NonEmptySet[A]]] =
    Arbitrary {
      if (parts <= 1 || parts > as.length) const(NonEmptyList.of(as))
      else {
        for {
          takeN <- chooseNum(1, as.length - parts + 1) // leave enough `as` to have at least 1 element per part
          newSet = as.toSortedSet.take(takeN.toInt)
          newSets <- splitIn(refineV[Positive](parts - 1).right.get, // parts > takeN
                             NonEmptySet.fromSetUnsafe(as.toSortedSet -- newSet)).arbitrary
        } yield NonEmptySet.fromSetUnsafe(newSet) :: newSets
      }
    }

  object TestMember {
    def apply(address: Address, status: MemberStatus): Member =
      apply(address, status, Set.empty[String])

    def apply(address: Address, status: MemberStatus, upNumber: Int, dc: ClusterSettings.DataCenter): Member =
      apply(address, status, Set.empty, dc, upNumber)

    def apply(address: Address,
              status: MemberStatus,
              roles: Set[String],
              dataCenter: ClusterSettings.DataCenter = ClusterSettings.DefaultDataCenter,
              upNumber: Int = Int.MaxValue): Member =
      withUniqueAddress(UniqueAddress(address, 0L), status, roles, dataCenter, upNumber)

    def withUniqueAddress(uniqueAddress: UniqueAddress,
                          status: MemberStatus,
                          roles: Set[String],
                          dataCenter: ClusterSettings.DataCenter,
                          upNumber: Int = Int.MaxValue): Member =
      new Member(uniqueAddress, upNumber, status, roles + (ClusterSettings.DcRolePrefix + dataCenter))
  }
}
