package akka.cluster.sbr.strategies.staticquorum

import akka.cluster.sbr.WorldView
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.posNum

object ArbitraryInstances extends akka.cluster.sbr.ArbitraryInstances {
  implicit val arbQuorumSize: Arbitrary[QuorumSize] = Arbitrary {
    posNum[Int].map(refineV[Positive](_).right.get) // trust me
  }

  implicit val arbReachableNodes: Arbitrary[ReachableNodes] = Arbitrary(
    for {
      worldView  <- arbitrary[WorldView]
      quorumSize <- arbitrary[QuorumSize]
      role       <- arbitrary[String]
    } yield ReachableNodes(worldView, quorumSize, role)
  )

  implicit val arbUnreachableNodes: Arbitrary[UnreachableNodes] = Arbitrary(
    for {
      worldView  <- arbitrary[WorldView]
      quorumSize <- arbitrary[QuorumSize]
      role       <- arbitrary[String]
    } yield UnreachableNodes(worldView, quorumSize, role)
  )
}
