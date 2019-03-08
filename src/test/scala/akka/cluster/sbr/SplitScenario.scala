package akka.cluster.sbr

import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.Member
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import akka.cluster.sbr.ArbitraryInstances._
import cats.Order
import cats.data.NonEmptySet

import scala.collection.immutable.SortedSet

final case class SplitScenario(reachabilities: List[Reachability]) extends AnyVal

object SplitScenario {
  implicit val memberOrder: Order[Member] = Order.fromOrdering

  implicit val arbSplitScenario: Arbitrary[SplitScenario] = Arbitrary(
    for {
      // every node starts from the same state
      reachability <- arbitrary[Reachability]

      // introduce some noise?

      // all nodes in the cluster
      nodes <- arbitrary[SortedSet[Member]]
      node <- arbitrary[Member]
      allNodes = NonEmptySet(node, nodes)

      // number of splits
      nSplits <- chooseNum(1, allNodes.length)

      // a reachability the has seen some cluster members become unreachable
      divergedReachability = someOf(allNodes.toSortedSet).map(
        _.map(UnreachableMember).foldLeft(reachability) {
          case (reachability, unreachableMember) => reachability.reachabilityEvent(unreachableMember)
        }
      )

      divergedReachabilities <- listOfN(nSplits, divergedReachability)
    } yield SplitScenario(divergedReachabilities)
  )

}
