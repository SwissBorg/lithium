package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import eu.timepit.refined.auto._
import org.scalacheck.Prop._
import org.scalacheck.Properties

class ReachableNodesSpec extends Properties("ReachableNodes") {
  property("reachableNodes") = forAll { (reachability: Reachability, quorumSize: QuorumSize) =>
    ReachableNodes(reachability, quorumSize) match {
      case Left(NoReachableNodesError) =>
        reachability.reachableNodes.isEmpty

      case Right(ReachableQuorum(reachableNodes)) =>
        reachableNodes.length >= quorumSize &&
          reachableNodes.toSortedSet.diff(reachability.reachableNodes).isEmpty

      case Right(ReachableSubQuorum(reachableNodes)) =>
        reachableNodes.length < quorumSize &&
          reachableNodes.toSortedSet.diff(reachability.reachableNodes).isEmpty
    }
  }
}
