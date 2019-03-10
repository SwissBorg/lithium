package akka.cluster.sbr

import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.Member
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.utils.splitIn
import cats.Order
import cats.data.{NonEmptyList, NonEmptySet}
import eu.timepit.refined.numeric.Positive
import shapeless.tag.@@

import scala.collection.immutable.SortedSet
import cats.data.NonEmptyList
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import org.scalacheck.Arbitrary
import org.scalacheck.Gen._

final case class Scenario(worldViews: List[WorldView]) extends AnyVal

object Scenario {
  sealed trait SplitTag
  type SplitScenario = Scenario @@ SplitTag

  implicit val memberOrder: Order[Member] = Order.fromOrdering

//  implicit val arbSplitScenario: Arbitrary[Scenario] = Arbitrary(
//    for {
//      worldView <- arbitrary[HealthyWorldView]
//      nodes = NonEmptySet.fromSetUnsafe(worldView.reachableNodes) // trust me
//
//      splits <- chooseNum(1, nodes.size.toLong).map(refineV[Positive](_).right.get) // trust me
//
//      divergedWorldViews <- splitIn(splits, NonEmptySet.from).arbitrary // trust me
//      a = divergedWorldViews.map(_.size)
//    } yield ???
//  )

//  implicit val arbSplitScenario: Arbitrary[Scenario] = Arbitrary(
//    for {
//      // every node starts from the same state
//      reachability <- arbitrary[WorldView]
//
//      // introduce some noise?
//
//      // all nodes in the cluster
//      nodes <- arbitrary[SortedSet[Member]]
//      node <- arbitrary[Member]
//      allNodes = NonEmptySet(node, nodes)
//
//      // number of splits
//      nSplits <- chooseNum(1, allNodes.length)
//
//      // a reachability the has seen some cluster members become unreachable
//      divergedReachability = someOf(allNodes.toSortedSet).map(
//        _.map(UnreachableMember).foldLeft(reachability) {
//          case (reachability, unreachableMember) => reachability.reachabilityEvent(unreachableMember)
//        }
//      )
//
//      divergedReachabilities <- listOfN(nSplits, divergedReachability)
//    } yield Scenario(divergedReachabilities)
//  )

}
