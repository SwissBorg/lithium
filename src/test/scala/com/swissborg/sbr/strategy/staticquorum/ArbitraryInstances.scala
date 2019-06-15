package com.swissborg.sbr.strategy.staticquorum

import com.swissborg.sbr.WorldView
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.posNum

object ArbitraryInstances extends com.swissborg.sbr.ArbitraryInstances {
  implicit val arbQuorumSize: Arbitrary[Int Refined Positive] = Arbitrary {
    posNum[Int].map(refineV[Positive](_).right.get) // trust me
  }

  implicit val arbReachableNodes: Arbitrary[ReachableNodes] = Arbitrary(
    for {
      worldView <- arbitrary[WorldView]
      quorumSize <- arbitrary[Int Refined Positive]
      role <- arbitrary[String]
    } yield ReachableNodes(worldView, quorumSize, role)
  )

  implicit val arbUnreachableNodes: Arbitrary[UnreachableNodes] = Arbitrary(
    for {
      worldView <- arbitrary[WorldView]
      quorumSize <- arbitrary[Int Refined Positive]
      role <- arbitrary[String]
    } yield UnreachableNodes(worldView, quorumSize, role)
  )
}
